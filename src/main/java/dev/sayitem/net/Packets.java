package dev.sayitem.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class Packets {
    private Packets() {}
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("sayitem", path));
    }

    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = Packets.type("request");
        public static final StreamCodec<FriendlyByteBuf, Request> CODEC = StreamCodec.unit(new Request());
        @Override public Type<Request> type() { return TYPE; }
    }
    public record SpeechReady() implements CustomPacketPayload {
        public static final Type<SpeechReady> TYPE = Packets.type("speech_ready");
        public static final StreamCodec<FriendlyByteBuf, SpeechReady> CODEC = StreamCodec.unit(new SpeechReady());
        @Override public Type<SpeechReady> type() { return TYPE; }
    }
    public record Challenge(String nonce, String item, String language, String languageName,
                            String whisperLanguage, String text, String reading, long remainingMillis, int pendingCount) implements CustomPacketPayload {
        public static final Type<Challenge> TYPE = Packets.type("challenge");
        public static final StreamCodec<FriendlyByteBuf, Challenge> CODEC = StreamCodec.of((b, p) -> {
            b.writeUtf(p.nonce, 36); b.writeUtf(p.item, 256); b.writeUtf(p.language, 32);
            b.writeUtf(p.languageName, 64); b.writeUtf(p.whisperLanguage, 3);
            b.writeUtf(p.text, 128); b.writeUtf(p.reading, 128); b.writeLong(p.remainingMillis); b.writeInt(p.pendingCount);
        }, b -> new Challenge(b.readUtf(36), b.readUtf(256), b.readUtf(32), b.readUtf(64),
                b.readUtf(3), b.readUtf(128), b.readUtf(128), b.readLong(), b.readInt()));
        @Override public Type<Challenge> type() { return TYPE; }
    }
    public record Attempt(String nonce, String transcript) implements CustomPacketPayload {
        public static final Type<Attempt> TYPE = Packets.type("attempt");
        public static final StreamCodec<FriendlyByteBuf, Attempt> CODEC = StreamCodec.of((b, p) -> {
            b.writeUtf(p.nonce, 36); b.writeUtf(p.transcript, 256);
        }, b -> new Attempt(b.readUtf(36), b.readUtf(256)));
        @Override public Type<Attempt> type() { return TYPE; }
    }
    public record SelectLanguage(String language) implements CustomPacketPayload {
        public static final Type<SelectLanguage> TYPE = Packets.type("language");
        public static final StreamCodec<FriendlyByteBuf, SelectLanguage> CODEC = StreamCodec.of(
                (b, p) -> b.writeUtf(p.language, 32), b -> new SelectLanguage(b.readUtf(32)));
        @Override public Type<SelectLanguage> type() { return TYPE; }
    }
    public record PackInfo(String languagesJson, String selected) implements CustomPacketPayload {
        public static final Type<PackInfo> TYPE = Packets.type("packs");
        public static final StreamCodec<FriendlyByteBuf, PackInfo> CODEC = StreamCodec.of((b, p) -> {
            b.writeUtf(p.languagesJson, 32767); b.writeUtf(p.selected, 32);
        }, b -> new PackInfo(b.readUtf(32767), b.readUtf(32)));
        @Override public Type<PackInfo> type() { return TYPE; }
    }
    public record Result(String nonce, boolean unlocked, boolean finished, int accuracy, String message) implements CustomPacketPayload {
        public static final Type<Result> TYPE = Packets.type("result");
        public static final StreamCodec<FriendlyByteBuf, Result> CODEC = StreamCodec.of((b, p) -> {
            b.writeUtf(p.nonce, 36); b.writeBoolean(p.unlocked); b.writeBoolean(p.finished); b.writeInt(p.accuracy); b.writeUtf(p.message, 512);
        }, b -> new Result(b.readUtf(36), b.readBoolean(), b.readBoolean(), b.readInt(), b.readUtf(512)));
        @Override public Type<Result> type() { return TYPE; }
    }
}
