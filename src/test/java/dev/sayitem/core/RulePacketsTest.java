package dev.sayitem.core;

import dev.sayitem.net.Packets;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RulePacketsTest {
    @Test void languageReloadPauseAndReadinessPacketsKeepTheStreamAligned() {
        var bytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            Packets.LanguageLoading.CODEC.encode(bytes, new Packets.LanguageLoading());
            Packets.SpeechReady.CODEC.encode(bytes, new Packets.SpeechReady());
            var choice = new Packets.SelectLanguage("random");
            Packets.SelectLanguage.CODEC.encode(bytes, choice);
            assertEquals(new Packets.LanguageLoading(), Packets.LanguageLoading.CODEC.decode(bytes));
            assertEquals(new Packets.SpeechReady(), Packets.SpeechReady.CODEC.decode(bytes));
            assertEquals(choice, Packets.SelectLanguage.CODEC.decode(bytes));
            assertEquals(0, bytes.readableBytes());
        } finally { bytes.release(); }
    }
    @Test void progressAndSharingSettingsSurviveNetworkEncoding() {
        var bytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var progress = new Packets.Progress(37, 1657, true);
            Packets.Progress.CODEC.encode(bytes, progress);
            assertEquals(progress, Packets.Progress.CODEC.decode(bytes));
            assertEquals(0, bytes.readableBytes());
            var edit = new Packets.SetSharing(false);
            Packets.SetSharing.CODEC.encode(bytes, edit);
            assertEquals(edit, Packets.SetSharing.CODEC.decode(bytes));
            assertEquals(0, bytes.readableBytes());
        } finally { bytes.release(); }
    }
    @Test void serverMetadataKeepsTimerAccuracyAndPermissionsInTheirOwnFields() {
        var bytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var original = new Packets.PackInfo("{\"pl\":\"Polski\"}", "pl", 73, 29, true);
            Packets.PackInfo.CODEC.encode(bytes, original);
            assertEquals(original, Packets.PackInfo.CODEC.decode(bytes));
            assertEquals(0, bytes.readableBytes());
        } finally { bytes.release(); }
    }
    @Test void timerEditsKeepBothRangeBoundariesOverTheWire() {
        var bytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (int seconds : new int[]{10, 15, 30}) {
                bytes.clear(); var edit = new Packets.SetTimer(seconds);
                Packets.SetTimer.CODEC.encode(bytes, edit);
                assertEquals(edit, Packets.SetTimer.CODEC.decode(bytes));
                assertEquals(0, bytes.readableBytes());
            }
        } finally { bytes.release(); }
    }
}
