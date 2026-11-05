package dev.sayitem.core;

import dev.sayitem.net.Packets;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RulePacketsTest {
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
