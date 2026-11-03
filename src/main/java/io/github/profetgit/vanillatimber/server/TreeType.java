package io.github.profetgit.vanillatimber.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * PRD 5.3: what a tree is made of. {@code data/<ns>/timber/tree_type/*.json}:
 * {@code {trunk: "#tag", crown: "#tag", rule: "distance"|"connected", decor: "#tag", max_crown_gap: 0}}.
 *
 * <p>{@code distance}: the crown is natural leaves (persistent=false) taken by their distance property, as vanilla
 * decays them; {@code crown} narrows them if given. {@code connected}: the crown is every crown block touching the
 * trunk or each other (through up to {@code max_crown_gap} other cells), within the radius.
 */
public record TreeType(TagKey<Block> trunk, Optional<TagKey<Block>> crown, Rule rule, Optional<TagKey<Block>> decor, int maxCrownGap) {
    public enum Rule implements StringRepresentable {
        DISTANCE("distance"), CONNECTED("connected");

        static final Codec<Rule> CODEC = StringRepresentable.fromEnum(Rule::values);
        private final String name;

        Rule(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final Codec<TreeType> CODEC = RecordCodecBuilder.create(i -> i.group(
        TagKey.hashedCodec(Registries.BLOCK).fieldOf("trunk").forGetter(TreeType::trunk),
        TagKey.hashedCodec(Registries.BLOCK).optionalFieldOf("crown").forGetter(TreeType::crown),
        Rule.CODEC.optionalFieldOf("rule", Rule.DISTANCE).forGetter(TreeType::rule),
        TagKey.hashedCodec(Registries.BLOCK).optionalFieldOf("decor").forGetter(TreeType::decor),
        Codec.intRange(0, 4).optionalFieldOf("max_crown_gap", 0).forGetter(TreeType::maxCrownGap)
    ).apply(i, TreeType::new));

    /** Overworld trees and the generic fallback: any log, natural leaves by the distance rule (1.3.6). */
    public static final TreeType OVERWORLD = new TreeType(BlockTags.LOGS, Optional.empty(), Rule.DISTANCE, Optional.empty(), 0);

    /** Natural leaves: vanilla's tag, or any {@link LeavesBlock} a mod adds without tagging it (PRD 5.3 fallback). */
    public static boolean naturalLeaf(BlockState s) {
        return (s.is(BlockTags.LEAVES) || s.getBlock() instanceof LeavesBlock) && s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT);
    }

    public boolean isCrown(BlockState s) {
        if (rule == Rule.DISTANCE) return naturalLeaf(s) && (crown.isEmpty() || s.is(crown.get()));
        return crown.isPresent() && s.is(crown.get());
    }

    public boolean isDecor(BlockState s) {
        return s.is(TreeScan.DECOR) || decor.isPresent() && s.is(decor.get());
    }
}
