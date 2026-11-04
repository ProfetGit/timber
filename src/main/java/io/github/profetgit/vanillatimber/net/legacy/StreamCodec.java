package io.github.profetgit.vanillatimber.net.legacy;

//? if <1.20.5 {
/*import java.util.function.BiConsumer;
import java.util.function.Function;

// stands in for 1.20.5's StreamCodec before it existed (dev/renames.json points the imports here)
public interface StreamCodec<B, V> {
    V decode(B buf);

    void encode(B buf, V value);

    static <B, V> StreamCodec<B, V> of(BiConsumer<B, V> encoder, Function<B, V> decoder) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buf) {
                return decoder.apply(buf);
            }

            @Override
            public void encode(B buf, V value) {
                encoder.accept(buf, value);
            }
        };
    }
}
*///?}
