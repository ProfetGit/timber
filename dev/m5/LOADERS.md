# M5 loader notes (link 15, 2026-11-03)

From decompiled loader sources (mc-source agent; nothing booted). Source keys under `~/.cache/mcsrc/`:
`neoforge-26.3.0.16-beta`, `minecraft-26.3` (NeoForge-patched MC, not vanilla `26.3`), `forge-26.3-66.0.3-sources`
(`src/patches/` = diffs vs vanilla), `fmlcore-26.3-66.0.3`, `javafmllanguage-26.3-66.0.3`, `eventbus-7.0.6`.

## State
- Seam done: `platform/Platform` (+ `Platform.Client`), `server/Hooks` (every server event, one order),
  `platform/fabric/FabricEntry` + `FabricClientEntry`. `platform/neoforge/NeoForgeEntry` + `NeoForgeClientEntry` written
  and compiling on 26.3 + 26.2. No Forge entry yet.
- Targets in `settings.gradle.kts`: 26.2 and 26.3 × fabric, neoforge, forge. All six `compileJava` green.
- 26.2 deltas, branched with `//? if >=26.3`: `LocalCoordinates.apply` (`Fx.local`; 26.2 uses
  `Vec3.applyLocalCoordinatesToRotation`), `Pack.open()` (stream in 26.3, one `PackResources` in 26.2; `Migrate`),
  `BakedQuad.MaterialInfo` (26.3 `shadeDirectionOverride`, 26.2 `boolean shade`: false = `Direction.UP`, which is what
  26.3's vanilla models now say with `shade_direction_override: up`; `MeshFall`).

## NeoForge 26.3 (events are classes; game bus `NeoForge.EVENT_BUS`, mod bus = constructor `IEventBus`)
- Lifecycle `event.server.ServerStarted/Stopping/StoppedEvent`; ticks `event.tick.LevelTickEvent.Pre/Post` (client and
  server levels: filter), `ServerTickEvent.Post`; `PlayerEvent.PlayerLoggedIn/OutEvent`; `EntityJoinLevelEvent` (chunk
  loads too, `loadedFromDisk`); `RegisterCommandsEvent`; `AddServerReloadListenersEvent.addListener(Identifier, listener)`.
- After /reload: `OnDatapackSyncEvent` with `getPlayer() == null` (only on a successful reload; per-player on join).
- Claims: **`event.level.block.BreakBlockEvent(Level, BlockPos, BlockState, Player)`** (BlockEvent.BreakEvent is gone);
  `post(ev).isCanceled()`. `CommonHooks.fireBlockBreak(level, gameType, player, pos, state)` also pre-cancels for tool /
  adventure / game-master blocks.
- Registry: mod-bus `RegisterEvent`, `event.register(Registries.TICKET_TYPE, id, supplier)` (entry currently calls
  `Hold.register()` = `Registry.register` inside the event: untested, switch to `event.register` if it throws).
- Payloads: mod-bus `RegisterPayloadHandlersEvent`, `registrar("1").optional()`, `playToServer/playToClient(TYPE,
  CODEC, handler)`; handlers run on the main thread by default. Send `PacketDistributor.sendToPlayer`, client
  `ClientPacketDistributor.sendToServer`; check `player.connection.hasChannel(type)` (false for vanilla clients;
  sending an unnegotiated payload throws).
- Client: `ClientPlayerNetworkEvent.LoggingIn/LoggingOut`, `LevelTickEvent.Post` on `ClientLevel`.
  `@Mod(value = ID, dist = Dist.CLIENT)` for the client entry.

## Forge 26.3 (EventBus 7: each event has a static `BUS`; mod-bus events `Event.getBus(context.getModBusGroup())`)
- `@Mod(ID)`, constructor `()` or `(FMLJavaModLoadingContext context)`. `ModList` is static (`ModList.isLoaded`,
  `getModContainerById(id).get().getModInfo().getVersion()`), `FMLPaths.CONFIGDIR.get()`, `FMLEnvironment.dist`.
- Records with `BUS`: `event.server.ServerStarted/Stopping/StoppedEvent(getServer)`, `TickEvent.LevelTickEvent.Pre/Post
  (side, level, ...)`, `TickEvent.ServerTickEvent.Post(..., server)`, `PlayerEvent.PlayerLoggedIn/OutEvent(getEntity)`,
  `EntityJoinLevelEvent` (cancellable bus), `RegisterCommandsEvent(getDispatcher, ...)`, `OnDatapackSyncEvent(getPlayerList,
  @Nullable getPlayer)`, `AddReloadListenerEvent.addListener(listener)` (no Identifier on Forge).
- Cancellable buses: `addListener(Predicate)` (true = cancel); `post(e)` returns true when cancelled. No `isCanceled()`.
- Claims: `BlockEvent.BreakEvent(level, pos, state, player, Result.DEFAULT)` (`net.minecraftforge.common.util.Result`);
  denied = `BreakEvent.BUS.post(ev) || ev.getResult().isDenied()` (Forge's own hook only checks the result).
- Registry: `RegisterEvent.getBus(modBusGroup).addListener(e -> e.register(Registries.TICKET_TYPE, id, supplier))`.
- Payloads: `ChannelBuilder.named(id).networkProtocolVersion(1).optional().payloadChannel().play(p ->
  p.clientbound(f -> f.add(TreeFall.TYPE, TreeFall.CODEC, handler)).serverbound(f -> f.addMain(Hello.TYPE, Hello.CODEC,
  handler))).build()` at construction (`optional()` accepts vanilla and missing peers). `add` runs on the network
  thread (`ctx.enqueueWork`, `ctx.setPacketHandled(true)`), `addMain` on the main thread. Send
  `channel.send(msg, PacketDistributor.PLAYER.with(player))` / `PacketDistributor.SERVER.noArg()`; check
  `channel.isRemotePresent(player.connection.getConnection())`.
- Client: `ClientPlayerNetworkEvent.LoggingIn.BUS/LoggingOut.BUS`, `TickEvent.LevelTickEvent.Post.BUS` with
  `side() == LogicalSide.CLIENT`.

## The chop on NeoForge/Forge (open)
Neither loader has an event after the block is gone (Fabric `PlayerBlockBreakEvents.AFTER`); their break events fire
at the head of `destroyBlock`, before removal, and `Falls.chop` expects the log gone (else the scan and loot count it
twice). `ServerPlayerGameMode.destroyBlock(BlockPos)Z` keeps vanilla's descriptor on both. Plan: one loader-neutral
mixin (`ServerPlayerGameModeMixin`, `//? if !fabric` or applied on all and Fabric's AFTER dropped): HEAD captures
`level.getBlockState(pos)` into a field/local, RETURN with `cir.getReturnValue() == true` calls
`Hooks.blockBroken(level, player, pos, state)`. Don't target `removeBlock` (each loader removes differently). Check it
with `check_mixins.py mods/Timber 26.3 --loader neoforge,forge`.
