package io.github.profetgit.vanillatimber.server;

import io.github.profetgit.vanillatimber.core.Cut;
import io.github.profetgit.vanillatimber.mixin.ServerPlayerGameModeAccess;
import io.github.profetgit.vanillatimber.net.ChopCells;
import io.github.profetgit.vanillatimber.net.Settings;
import io.github.profetgit.vanillatimber.platform.Platform;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * PRD 5.13 chop mode: a survival break of a trunk log that the player is mining is a chop. The log stays and its layer's
 * cut goes one chop deeper: the cut is the tree's trunk cells at that height that touch the chopped one (a 2x2 trunk's
 * four), so chops on any of them add up and another height starts its own cut. The chop that reaches the count is let
 * through; {@link Falls#chop} breaks the rest of the layer with drops and fells the tree from it. Everything is on the
 * server; a vanilla client only sees the log vanish for one round trip (the mask display covers it).
 */
public final class Chops {
    private static final ServerboundPlayerActionPacket.Action START = ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK;
    private static final ServerboundPlayerActionPacket.Action STOP = ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK;
    private static final int RESEND = 200, MASK_TICKS = 60;
    private static final double NEAR = 192.0;

    /** The mining packet being handled, and the player whose delayed break is running: the only breaks that are the player's mining. */
    private static ServerPlayer ctxPlayer, ticking;
    private static BlockPos ctxPos;
    private static ServerboundPlayerActionPacket.Action ctxAction;

    /** The final chop of a tree: the tool it was made with and the layer it cuts through (packed cells, the chopped one too). */
    record Final(UUID player, BlockPos pos, int tick, ItemStack tool, long base, long[] cut) {}

    private static Final fin;
    /** Cuts that went with a fall, cleared on modded clients once the server has sent the blocks' removal (a tick later). */
    private static final List<ClearLater> LATER = new ArrayList<>();

    private record ClearLater(ServerLevel level, int tick, ChopCells msg) {}
    /** The mining tick (the game mode's own counter) of each player's last counted chop. */
    private static final Map<UUID, Integer> LAST = new HashMap<>();
    private static final Map<UUID, Mask> MASKS = new HashMap<>();
    private static Settings sent;

    /** Self-test probes: cracks and creaks sent, and the last crack. */
    public static volatile int cracks, creaks, lastStage = -2;
    public static volatile BlockPos lastCrack;
    /** Self-test probes: cell messages sent to modded viewers, and the last one. */
    public static volatile int cellMessages;
    public static volatile ChopCells lastCells;

    private Chops() {}

    /** needed = max(1, min(logs, round(1 + a ln(1 + (logs - 1) / a)))), HT's TreeChop curve. */
    public static int needed(int logs, int curve) {
        double a = curve;
        return Math.max(1, Math.min(logs, (int) Math.round(1 + a * Math.log(1 + (logs - 1) / a))));
    }

    /** PRD 5.1 gates for chopping: the toggle, game mode and sneak mode; with {@code require_axe} an axe or an empty hand. */
    static boolean mayChop(ServerPlayer player) {
        if (!Falls.gates(player)) return false;
        ItemStack tool = player.getMainHandItem();
        return !Config.on(Config.Key.REQUIRE_AXE) || tool.isEmpty() || tool.is(ItemTags.AXES) || tool.is(Falls.C_AXES);
    }

    // ---- the mining packet and the delayed break (bracketed by ChopMixin) ----

    public static void actionIn(ServerPlayer player, ServerLevel level, BlockPos pos, ServerboundPlayerActionPacket.Action action) {
        ctxPlayer = player;
        ctxPos = pos;
        ctxAction = action;
        if (action != START || !Config.on(Config.Key.CHOPS) || Migrate.inert || Viewers.local(player)) return;
        BlockState state = level.getBlockState(pos);
        if (TreeTypes.forTrunk(state) == null || !mayChop(player) || !player.isWithinBlockInteractionRange(pos, 1.0)) return;
        if (Config.on(Config.Key.PROTECT) && Placed.has(level, pos)) return;
        mask(player, level, pos, state);
    }

    public static void actionOut() {
        ctxPlayer = null;
    }

    public static void tickIn(ServerPlayer player) {
        ticking = player;
    }

    public static void tickOut() {
        ticking = null;
    }

    /** The game mode tick the player's mining of {@code pos} started at, or -1: this break isn't the player's own mining. */
    private static int miningStart(ServerPlayer player, ServerPlayerGameModeAccess gm, BlockPos pos) {
        if (ctxPlayer == player && pos.equals(ctxPos)) {
            if (ctxAction == START) return gm.timber$gameTicks();
            if (ctxAction == STOP) return gm.timber$destroyProgressStart();
        }
        if (ticking == player && pos.equals(gm.timber$delayedDestroyPos())) return gm.timber$delayedTickStart();
        return -1;
    }

    // ---- the chop ----

    /** True: the break is cancelled (the log stays). False: vanilla breaks it, and {@link Falls#chop} fells only if {@link #takeFinal} says so. */
    public static boolean intercept(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!Config.on(Config.Key.CHOPS) || Migrate.inert) return false;
        BlockState state = level.getBlockState(pos);
        if (TreeTypes.forTrunk(state) == null) return false;
        ServerPlayerGameModeAccess gm = (ServerPlayerGameModeAccess) player.gameMode;
        int start = miningStart(player, gm, pos);
        if (start < 0 || !mayChop(player)) return false;
        ItemStack tool = player.getMainHandItem();
        if (!tool.canDestroyBlock(state, level, pos, player) || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) return false;
        if (Config.on(Config.Key.PROTECT) && Placed.has(level, pos) || Falls.busy(level, pos)) return false;
        // the rate limit comes before the add-on hook and the scan, so a client spamming STOP costs no scans
        int now = gm.timber$gameTicks();
        Integer lastChop = LAST.get(player.getUUID());
        if (lastChop != null && lastChop == now) return true;
        int from = lastChop == null ? start : Math.max(start, lastChop);
        if (state.getDestroyProgress(player, level, pos) * (now - from + 1) < 0.7F) return true;
        Component why = Api.cancel(level, player, pos, state);
        if (why != null) {
            if (!why.getString().isEmpty()) Falls.refuse(player, why);
            return false;
        }
        Tree tree = TreeScan.scan(level, pos, state, player.getYRot());
        if (tree == null) {
            if (TreeScan.tooBig) Falls.refuse(player, "toobig", "Timber: this tree is too big to fell");
            return false;
        }
        if (!Falls.allowed(level, player, tree)) {
            Falls.refuse(player, "protected", "Timber: part of this tree is protected");
            return false;
        }
        ChopData data = ChopData.get(level);
        long key = tree.base().asLong();
        long[] layer = layer(tree, pos, state);
        ChopData.Entry e = data.find(key, layer);
        int needed = needed(tree.total(), Config.get(Config.Key.CHOP_CURVE));
        int chops = (e == null ? 0 : e.chops) + 1;
        LAST.put(player.getUUID(), now);
        if (chops >= needed) {
            fin = new Final(player.getUUID(), pos, level.getServer().getTickCount(), tool.copy(), key, layer);
            return false;
        }
        if (e == null) e = data.create(key, pos.asLong());
        else regrow(level, e, layer);
        e.chops = chops;
        e.last = pos.asLong();
        e.needed = needed;
        for (long c : layer) e.cells.add(c);
        data.setDirty();
        crack(level, e);
        sendCells(level, e, pos);
        tool.mineBlock(level, state, pos, player);
        player.causeFoodExhaustion(0.005F);
        fx(level, player, tree, pos, state, chops == needed - 1);
        Mask m = MASKS.get(player.getUUID());
        if (m != null) m.seen = level.getGameTime();
        return true;
    }

    /**
     * {@link Falls#chop} asks after a break: was it the final chop of a tree? Every cut of that tree goes (the final
     * layer's cells on modded clients a tick later, once their removal is on its way), and the mask with it.
     */
    static Final takeFinal(ServerLevel level, ServerPlayer player, BlockPos pos) {
        Final f = fin;
        fin = null;
        if (f == null || !f.player().equals(player.getUUID()) || !f.pos().equals(pos) || f.tick() != level.getServer().getTickCount()) return null;
        ChopData data = ChopData.get(level);
        for (ChopData.Entry e : data.of(f.base())) {
            for (long c : e.cells) crack(level, c, -1);
            ChopCells clear = message(level, e, true);
            if (e.overlaps(f.cut())) LATER.add(new ClearLater(level, level.getServer().getTickCount(), clear));
            else sendNear(level, BlockPos.of(e.last), clear);
            data.remove(e);
        }
        dropMask(player.getUUID());
        return f;
    }

    /**
     * The final chop's layer, after the scan: the cells vanilla didn't break go now, each dropping its loot rolled with
     * the tool of the last chop (no extra durability), with the break's chips and sound.
     */
    static void breakCut(ServerLevel level, ServerPlayer player, Final f) {
        for (long c : f.cut()) {
            BlockPos q = BlockPos.of(c);
            if (q.equals(f.pos())) continue;
            BlockState s = level.getBlockState(q);
            if (TreeTypes.forTrunk(s) == null) continue;
            BlockEntity be = level.getBlockEntity(q);
            Placed.remove(level, q);
            level.destroyBlock(q, false, player);
            Block.dropResources(s, level, q, be, player, f.tool());
        }
    }

    /** The cut is a layer: this tree's upright trunk cells at the chopped one's height that touch it side by side (3x3 tiles on wider trunks); a sideways log is its own cut. */
    static long[] layer(Tree tree, BlockPos pos, BlockState state) {
        if (!upright(state)) return new long[] {pos.asLong()};
        LongOpenHashSet trunk = new LongOpenHashSet();
        trunk.add(pos.asLong());
        for (Tree.Part p : tree.parts()) if (!p.leaf() && p.pos().getY() == pos.getY() && upright(p.state())) trunk.add(p.pos().asLong());
        LongArrayList found = new LongArrayList();
        LongOpenHashSet seen = new LongOpenHashSet();
        found.add(pos.asLong());
        seen.add(pos.asLong());
        int minX = pos.getX(), maxX = minX, minZ = pos.getZ(), maxZ = minZ;
        for (int i = 0; i < found.size() && found.size() < 64; i++) {
            BlockPos p = BlockPos.of(found.getLong(i));
            for (Direction d : Direction.Plane.HORIZONTAL) {
                long q = p.relative(d).asLong();
                if (!trunk.contains(q) || !seen.add(q)) continue;
                found.add(q);
                minX = Math.min(minX, BlockPos.getX(q));
                maxX = Math.max(maxX, BlockPos.getX(q));
                minZ = Math.min(minZ, BlockPos.getZ(q));
                maxZ = Math.max(maxZ, BlockPos.getZ(q));
            }
        }
        int tx = minX + 3 * Math.floorDiv(pos.getX() - minX, 3), tz = minZ + 3 * Math.floorDiv(pos.getZ() - minZ, 3);
        boolean tiled = maxX - minX >= 3 || maxZ - minZ >= 3;
        LongArrayList out = new LongArrayList();
        for (long c : found) {
            int x = BlockPos.getX(c), z = BlockPos.getZ(c);
            if (!tiled || x >= tx && x <= tx + 2 && z >= tz && z <= tz + 2) out.add(c);
        }
        long[] cells = out.toLongArray();
        Arrays.sort(cells);
        return cells;
    }

    static boolean upright(BlockState state) {
        return state.getOptionalValue(BlockStateProperties.AXIS).map(a -> a == Direction.Axis.Y).orElse(true);
    }

    /** A cut's layer changed shape (a cell broken some other way, an entry saved before the layers): cells that left it are whole again. */
    private static void regrow(ServerLevel level, ChopData.Entry e, long[] layer) {
        LongOpenHashSet now = new LongOpenHashSet(layer);
        LongArrayList gone = new LongArrayList();
        for (long c : e.cells) if (!now.contains(c)) gone.add(c);
        if (gone.isEmpty()) return;
        for (long c : gone) {
            e.cells.remove(c);
            crack(level, c, -1);
        }
        int[] whole = new int[gone.size()];
        Arrays.fill(whole, Cut.WHOLE);
        sendNear(level, BlockPos.of(e.last), new ChopCells(false, gone.toLongArray(), whole));
    }

    /** Each chopped cell has its own crack, so every cell of a layer shows one. */
    private static int crackId(long cell) {
        return -1 - (int) ((cell * -7046029254386353131L) >>> 34);
    }

    /** Every cell of the cut cracks; on a layer of several, every other cell one stage less, so a face doesn't show one crack stamped twice. */
    private static void crack(ServerLevel level, ChopData.Entry e) {
        int stage = Math.clamp(e.chops * 10 / Math.max(1, e.needed), 0, 9);
        for (long c : e.cells) {
            boolean odd = e.cells.size() > 1 && ((BlockPos.getX(c) + BlockPos.getZ(c)) & 1) == 1;
            crack(level, c, odd ? Math.max(0, stage - 1) : stage);
        }
        lastCrack = BlockPos.of(e.last);
    }

    private static void crack(ServerLevel level, long cell, int stage) {
        BlockPos pos = BlockPos.of(cell);
        level.destroyBlockProgress(crackId(cell), pos, stage);
        cracks++;
        lastStage = stage;
        lastCrack = pos;
    }

    /** The break's chips and sound, a few leaves from the crown, the creak on the chop before the last. */
    private static void fx(ServerLevel level, ServerPlayer player, Tree tree, BlockPos pos, BlockState state, boolean creak) {
        // a vanilla client plays its own predicted break; a modded client doesn't predict, so it hears this one
        level.levelEvent(Viewers.local(player) ? null : player, 2001, pos, Block.getId(state));
        if (tree.leaf() != null) {
            BlockPos top = pos;
            for (Tree.Part p : tree.parts()) if (!p.leaf() && p.pos().getY() > top.getY()) top = p.pos();
            double r = tree.cw() * 9 / 20 / 1000.0;
            Fx.particle(level, Fx.leafParticle(tree.leaf()), Vec3.atCenterOf(top).add(0, 1, 0), r, 0.8, r, 0, 3);
        }
        if (creak) {
            Fx.creak(level, Vec3.atCenterOf(pos));
            creaks++;
        }
    }

    // ---- the mask: the log a vanilla client's prediction takes away for one round trip ----

    private static final class Mask {
        final ServerPlayer player;
        final ServerLevel level;
        final BlockPos pos;
        final Display.BlockDisplay display;
        long seen;

        Mask(ServerPlayer player, ServerLevel level, BlockPos pos, Display.BlockDisplay display) {
            this.player = player;
            this.level = level;
            this.pos = pos;
            this.display = display;
            this.seen = level.getGameTime();
        }
    }

    private static void mask(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        Mask m = MASKS.get(player.getUUID());
        if (m != null && m.level == level && m.pos.equals(pos) && !m.display.isRemoved()) {
            m.seen = level.getGameTime();
            return;
        }
        dropMask(player.getUUID());
        int light = Displays.lightAt(level, pos);
        Display.BlockDisplay d = Displays.spawn(level, state, pos.getX(), pos.getY(), pos.getZ(), 0f, 0f,
            Displays.of(0.003, 0.003, 0.003, new Quaternionf(), 0.994, 0.994, 0.994, new Quaternionf()));
        Displays.light(d, LightCoordsUtil.block(light), LightCoordsUtil.sky(light));
        MASKS.put(player.getUUID(), new Mask(player, level, pos, d));
    }

    private static void dropMask(UUID player) {
        Mask m = MASKS.remove(player);
        if (m != null && !m.display.isRemoved()) m.display.discard();
    }

    /** Tests: the mask display of a player, or null. */
    public static Display.BlockDisplay maskOf(ServerPlayer player) {
        Mask m = MASKS.get(player.getUUID());
        return m == null || m.display.isRemoved() ? null : m.display;
    }

    // ---- per tick ----

    /** At the start of a level's tick: the masks' lifetimes, the clears owed, and every 10 s the cracks again (vanilla clients drop one after 20 s). */
    public static void tick(ServerLevel level) {
        ctxPlayer = null;
        ticking = null;
        long now = level.getGameTime();
        if (!MASKS.isEmpty()) {
            for (Iterator<Mask> it = MASKS.values().iterator(); it.hasNext(); ) {
                Mask m = it.next();
                if (m.level != level) continue;
                ServerPlayerGameModeAccess gm = (ServerPlayerGameModeAccess) m.player.gameMode;
                if (gm.timber$isDestroyingBlock() && m.pos.equals(gm.timber$destroyPos())) m.seen = now;
                if (m.display.isRemoved() || m.player.isRemoved() || now - m.seen > MASK_TICKS || TreeTypes.forTrunk(level.getBlockState(m.pos)) == null) {
                    if (!m.display.isRemoved()) m.display.discard();
                    it.remove();
                }
            }
        }
        if (!LATER.isEmpty()) {
            int tick = level.getServer().getTickCount();
            for (Iterator<ClearLater> it = LATER.iterator(); it.hasNext(); ) {
                ClearLater c = it.next();
                if (c.level() != level || c.tick() >= tick) continue;
                sendNear(level, BlockPos.of(c.msg().cells()[0]), c.msg());
                it.remove();
            }
        }
        if (now % RESEND != 0 || !Config.on(Config.Key.CHOPS)) return;
        ChopData data = ChopData.get(level);
        for (ChopData.Entry e : data.all()) {
            BlockPos last = BlockPos.of(e.last);
            if (!level.hasChunkAt(last)) continue;
            LongArrayList gone = new LongArrayList();
            for (long c : e.cells) if (level.hasChunkAt(BlockPos.of(c)) && !Placed.isLog(level.getBlockState(BlockPos.of(c)))) gone.add(c);
            for (long c : gone) {
                e.cells.remove(c);
                crack(level, c, -1);
            }
            if (!gone.isEmpty()) data.setDirty();
            if (e.cells.isEmpty()) data.remove(e);
            else crack(level, e);
        }
        sendAll(level);
    }

    // ---- the narrowed logs for modded clients ----

    /** The box each cell of a cut still fills: the layer's footprint shrunk around its centre, or a lone sideways log shrunk around its own. */
    static int box(ServerLevel level, ChopData.Entry e, long cell) {
        int step = Cut.step(e.chops, e.needed);
        if (e.cells.size() == 1) {
            BlockState s = level.getBlockState(BlockPos.of(cell));
            if (!upright(s)) return Cut.across(s.getValue(BlockStateProperties.AXIS).ordinal(), step);
        }
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (long c : e.cells) {
            x0 = Math.min(x0, BlockPos.getX(c));
            x1 = Math.max(x1, BlockPos.getX(c));
            z0 = Math.min(z0, BlockPos.getZ(c));
            z1 = Math.max(z1, BlockPos.getZ(c));
        }
        return Cut.upright(x0, x1, z0, z1, BlockPos.getX(cell), BlockPos.getZ(cell), step);
    }

    private static ChopCells message(ServerLevel level, ChopData.Entry e, boolean clear) {
        long[] cells = e.cells.toLongArray();
        int[] boxes = new int[cells.length];
        for (int i = 0; i < cells.length; i++) boxes[i] = clear ? Cut.WHOLE : box(level, e, cells[i]);
        return new ChopCells(false, cells, boxes);
    }

    private static void sendNear(ServerLevel level, BlockPos at, ChopCells msg) {
        if (msg.cells().length == 0) return;
        Vec3 c = Vec3.atCenterOf(at);
        for (ServerPlayer p : level.players()) {
            if (!Viewers.local(p) || p.position().distanceToSqr(c) > NEAR * NEAR || !Platform.get().canSend(p, ChopCells.TYPE)) continue;
            Platform.get().send(p, msg);
            cellMessages++;
            lastCells = msg;
        }
    }

    /** After a chop: every cell of the cut, to the modded viewers near it. */
    private static void sendCells(ServerLevel level, ChopData.Entry e, BlockPos at) {
        sendNear(level, at, message(level, e, false));
    }

    private static ChopCells all(ServerLevel level) {
        List<ChopData.Entry> entries = ChopData.get(level).all();
        LongArrayList cells = new LongArrayList();
        it.unimi.dsi.fastutil.ints.IntArrayList boxes = new it.unimi.dsi.fastutil.ints.IntArrayList();
        for (ChopData.Entry e : entries) {
            if (!level.hasChunkAt(BlockPos.of(e.last))) continue;
            for (long c : e.cells) {
                cells.add(c);
                boxes.add(box(level, e, c));
            }
        }
        return new ChopCells(true, cells.toLongArray(), boxes.toIntArray());
    }

    /** The level's full set to every modded viewer in it (the 200-tick resend: late joiners and walkers-up heal). */
    private static void sendAll(ServerLevel level) {
        ChopCells msg = null;
        for (ServerPlayer p : level.players()) {
            if (!Viewers.local(p) || !Platform.get().canSend(p, ChopCells.TYPE)) continue;
            if (msg == null) msg = all(level);
            Platform.get().send(p, msg);
            cellMessages++;
            lastCells = msg;
        }
    }

    /** A modded client's Hello: the narrowed logs of the level it is in. */
    public static void sendCells(ServerPlayer player) {
        if (!Viewers.local(player) || !Platform.get().canSend(player, ChopCells.TYPE)) return;
        Platform.get().send(player, all((ServerLevel) player.level()));
        cellMessages++;
    }

    // ---- settings for modded clients ----

    private static Settings settings() {
        Config.Key[] keys = Config.Key.values();
        String[] names = new String[keys.length];
        int[] values = new int[keys.length];
        for (int i = 0; i < keys.length; i++) {
            names[i] = keys[i].json;
            values[i] = Config.get(keys[i]);
        }
        return new Settings(names, values);
    }

    /** A modded client's join or Hello: the server's settings (chop mode's three, and every value for the settings screen). */
    public static void sendSettings(ServerPlayer player) {
        if (Platform.get().canSend(player, Settings.TYPE)) Platform.get().send(player, settings());
    }

    /** Every second: when the config changed, every modded client hears it. */
    public static void sync(MinecraftServer server) {
        Settings now = settings();
        if (now.same(sent)) return;
        sent = now;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sendSettings(p);
    }

    public static void leave(ServerPlayer player) {
        LAST.remove(player.getUUID());
        dropMask(player.getUUID());
    }

    static void stopping() {
        fin = null;
        ctxPlayer = null;
        ticking = null;
        sent = null;
        LAST.clear();
        LATER.clear();
        for (Mask m : MASKS.values()) if (!m.display.isRemoved()) m.display.discard();
        MASKS.clear();
    }

    // ---- tests ----

    /** Cuts in the level (tests). */
    public static int entries(ServerLevel level) {
        return ChopData.get(level).all().size();
    }

    /** {chops, needed, last cell as packed long} of the first cut, or null (tests). */
    public static long[] progress(ServerLevel level) {
        List<ChopData.Entry> all = ChopData.get(level).all();
        if (all.isEmpty()) return null;
        ChopData.Entry e = all.getFirst();
        return new long[] {e.chops, e.needed, e.last};
    }

    /** {chops, needed, cells...} of the cut holding {@code pos}, or null (tests). */
    public static long[] cutAt(ServerLevel level, BlockPos pos) {
        for (ChopData.Entry e : ChopData.get(level).all()) {
            if (!e.cells.contains(pos.asLong())) continue;
            long[] out = new long[e.cells.size() + 2];
            out[0] = e.chops;
            out[1] = e.needed;
            int i = 2;
            for (long c : e.cells) out[i++] = c;
            return out;
        }
        return null;
    }

    /** Tests: forget every tree's progress in the level and every player's last chop. */
    public static void clear(ServerLevel level) {
        ChopData data = ChopData.get(level);
        for (ChopData.Entry e : data.all()) {
            for (long c : e.cells) crack(level, c, -1);
            sendNear(level, BlockPos.of(e.last), message(level, e, true));
            data.remove(e);
        }
        LAST.clear();
    }

    /** Tests: the box (packed, {@link Cut}) the cell at {@code pos} still fills, {@link Cut#WHOLE} when it isn't cut. */
    public static int cellBox(ServerLevel level, BlockPos pos) {
        for (ChopData.Entry e : ChopData.get(level).all()) if (e.cells.contains(pos.asLong())) return box(level, e, pos.asLong());
        return Cut.WHOLE;
    }
}
