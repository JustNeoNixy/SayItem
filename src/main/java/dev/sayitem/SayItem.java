package dev.sayitem;

import dev.sayitem.core.*;
import dev.sayitem.net.Packets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.IOException;
import java.util.*;

public final class SayItem implements ModInitializer {
    private static PackRepository packs;
    private static RunState run = new RunState();
    private static final Map<UUID, String> lastSent = new HashMap<>();
    private static final Map<UUID, Long> requests = new HashMap<>();
    private static long now() { return System.nanoTime() / 1_000_000; }

    @Override public void onInitialize() {
        try { packs = new PackRepository(FabricLoader.getInstance().getConfigDir().resolve("sayitem/languages")); }
        catch (IOException e) { throw new IllegalStateException("Cannot load SayItem packs", e); }
        PayloadTypeRegistry.serverboundPlay().register(Packets.Request.TYPE, Packets.Request.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Packets.Attempt.TYPE, Packets.Attempt.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Packets.SelectLanguage.TYPE, Packets.SelectLanguage.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Packets.Challenge.TYPE, Packets.Challenge.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Packets.Result.TYPE, Packets.Result.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Packets.PackInfo.TYPE, Packets.PackInfo.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendPacks(handler.player));
        ServerPlayNetworking.registerGlobalReceiver(Packets.Request.TYPE, (packet, context) -> {
            var p = context.player(); long time = now();
            if (requests.containsKey(p.getUUID()) && time - requests.get(p.getUUID()) < 250) return;
            requests.put(p.getUUID(), time); scan(p, time); sendCurrent(p, time, true);
        });
        ServerPlayNetworking.registerGlobalReceiver(Packets.Attempt.TYPE, (packet, context) -> attempt(context.player(), packet));
        ServerPlayNetworking.registerGlobalReceiver(Packets.SelectLanguage.TYPE, (packet, context) -> selectLanguage(context.player(), packet.language()));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try { packs = new PackRepository(FabricLoader.getInstance().getConfigDir().resolve("sayitem/languages")); }
            catch (IOException e) { throw new IllegalStateException("Cannot reload SayItem packs", e); }
            run = new RunState(); lastSent.clear(); requests.clear();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { run = new RunState(); lastSent.clear(); requests.clear(); });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            lastSent.remove(handler.player.getUUID()); requests.remove(handler.player.getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long time = now();
            for (var player : server.getPlayerList().getPlayers()) { scan(player, time); sendCurrent(player, time, false); }
        });
        UseItemCallback.EVENT.register((p, w, h) -> p instanceof ServerPlayer sp && !allow(sp, p.getItemInHand(h)) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((p, w, h, hit) -> p instanceof ServerPlayer sp && !allow(sp, p.getItemInHand(h)) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((p, w, h, e, hit) -> p instanceof ServerPlayer sp && !allow(sp, p.getItemInHand(h)) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((p, w, h, e, hit) -> p instanceof ServerPlayer sp && !allow(sp, p.getItemInHand(h)) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((p, w, h, pos, face) -> p instanceof ServerPlayer sp && !allow(sp, p.getItemInHand(h)) ? InteractionResult.FAIL : InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((w, p, pos, state, e) -> !(p instanceof ServerPlayer sp) || allow(sp, p.getMainHandItem()));
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> dispatcher.register(Commands.literal("sayitem")
            .executes(ctx -> {
                var state = run.player(ctx.getSource().getPlayerOrException().getUUID());
                ctx.getSource().sendSuccess(() -> Component.literal("SayItem · " + state.language + " · " + state.unlocked.size()
                    + " unlocked this run · 15 seconds · 90% required"), false); return 1;
            })
            .then(Commands.literal("challenge").executes(ctx -> { sendCurrent(ctx.getSource().getPlayerOrException(), now(), true); return 1; }))
            .then(Commands.literal("language").then(Commands.argument("id", StringArgumentType.word())
                .suggests((ctx, b) -> { packs.ids().stream().sorted().filter(id -> id.startsWith(b.getRemaining())).forEach(b::suggest); return b.buildFuture(); })
                .executes(ctx -> selectLanguage(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id")) ? 1 : 0)))));
    }
    private static boolean selectLanguage(ServerPlayer p, String language) {
        if (packs.get(language) == null) {
            result(p, "", false, false, -1, "This server has no “" + language + "” pack. Import it and restart the world/server."); return false;
        }
        run.player(p.getUUID()).language = language; sendPacks(p); sendCurrent(p, now(), true); return true;
    }
    private static void sendPacks(ServerPlayer p) {
        var names = new java.util.TreeMap<String, String>();
        for (String id : packs.ids()) names.put(id, packs.get(id).name());
        if (ServerPlayNetworking.canSend(p, Packets.PackInfo.TYPE))
            ServerPlayNetworking.send(p, new Packets.PackInfo(JsonFiles.GSON.toJson(names), run.player(p.getUUID()).language));
    }
    private static String id(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    private static boolean bypass(ServerPlayer p) { return p.isCreative() || p.isSpectator(); }
    private static void scan(ServerPlayer p, long time) {
        if (bypass(p)) return;
        var state = run.player(p.getUUID());
        for (var round : List.copyOf(state.rounds.values())) {
            if (round.expired(time)) {
                removeItem(p, round.item()); state.rounds.remove(round.item());
                result(p, round.nonce(), false, true, -1, "Time's up. Item removed — collect it again to retry.");
            }
        }
        var inventory = p.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) collect(state, inventory.getItem(slot), time);
        for (var slot : EquipmentSlot.values()) collect(state, p.getItemBySlot(slot), time);
        collect(state, p.containerMenu.getCarried(), time);
    }
    private static void collect(RunState.PlayerRun state, ItemStack stack, long time) {
        if (!stack.isEmpty()) state.collect(id(stack), time);
    }
    private static void removeItem(ServerPlayer p, String item) {
        var inventory = p.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
            if (!inventory.getItem(slot).isEmpty() && id(inventory.getItem(slot)).equals(item)) inventory.setItem(slot, ItemStack.EMPTY);
        for (var slot : EquipmentSlot.values())
            if (!p.getItemBySlot(slot).isEmpty() && id(p.getItemBySlot(slot)).equals(item)) p.setItemSlot(slot, ItemStack.EMPTY);
        if (!p.containerMenu.getCarried().isEmpty() && id(p.containerMenu.getCarried()).equals(item)) p.containerMenu.setCarried(ItemStack.EMPTY);
        p.inventoryMenu.broadcastChanges(); p.containerMenu.broadcastChanges();
    }
    public static boolean allow(ServerPlayer p, ItemStack stack) {
        if (stack.isEmpty() || bypass(p) || run.player(p.getUUID()).unlocked.contains(id(stack))) return true;
        run.player(p.getUUID()).collect(id(stack), now()); sendCurrent(p, now(), true); return false;
    }
    private static PackRepository.Phrase phrase(ServerPlayer p, String item) {
        var type = BuiltInRegistries.ITEM.getValue(Identifier.parse(item));
        return packs.phrase(run.player(p.getUUID()).language, item, type.getDefaultInstance().getHoverName().getString());
    }
    private static void sendCurrent(ServerPlayer p, long time, boolean force) {
        var state = run.player(p.getUUID()); var current = state.current();
        if (current == null || current.expired(time) || !ServerPlayNetworking.canSend(p, Packets.Challenge.TYPE)) return;
        String signature = current.nonce() + state.language + current.remaining(time) / 1000 + ":" + state.rounds.size();
        if (!force && signature.equals(lastSent.get(p.getUUID()))) return;
        lastSent.put(p.getUUID(), signature);
        var phrase = phrase(p, current.item());
        ServerPlayNetworking.send(p, new Packets.Challenge(current.nonce(), current.item(), state.language,
            phrase.languageName(), phrase.whisperLanguage(), phrase.entry().text(), phrase.entry().reading(), current.remaining(time), state.rounds.size() - 1));
    }
    private static void attempt(ServerPlayer p, Packets.Attempt attempt) {
        long time = now(); var state = run.player(p.getUUID());
        var round = state.rounds.values().stream().filter(r -> r.nonce().equals(attempt.nonce())).findFirst().orElse(null);
        if (round == null) return;
        if (round.expired(time)) { scan(p, time); return; }
        var entry = phrase(p, round.item()).entry();
        double score = AnswerMatcher.score(attempt.transcript(), entry.text(), entry.aliases());
        int percent = (int) Math.floor(score * 100 + 1e-8);
        if (score >= 0.9) {
            state.unlock(round.item());
            result(p, round.nonce(), true, true, percent, "Unlocked for this run! " + percent + "% match."); sendCurrent(p, time, true);
        } else {
            result(p, round.nonce(), false, false, percent, attempt.transcript().isBlank()
                ? "No speech heard. Try again before time runs out."
                : "Heard: “" + attempt.transcript() + "” · " + percent + "% (need 90%).");
        }
    }
    private static void result(ServerPlayer p, String nonce, boolean success, boolean finished, int accuracy, String text) {
        if (ServerPlayNetworking.canSend(p, Packets.Result.TYPE)) ServerPlayNetworking.send(p, new Packets.Result(nonce, success, finished, accuracy, text));
    }
}
