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
import net.minecraft.world.level.storage.LevelResource;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.IOException;
import java.util.*;

public final class SayItem implements ModInitializer {
    private static PackRepository packs;
    private static UnlockProgress progress = new UnlockProgress();
    private static RunState run = new RunState(progress);
    private static java.nio.file.Path progressFile;
    private static Set<String> registeredItems = Set.of();
    private static long lastSaveAttempt;
    private static boolean progressSaveFailed;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("sayitem");
    private static ServerRules rules = new ServerRules();
    private static java.nio.file.Path rulesFile() { return FabricLoader.getInstance().getConfigDir().resolve("sayitem/rules.json"); }
    private static final Map<UUID, String> lastSent = new HashMap<>();
    private static final Map<UUID, Long> requests = new HashMap<>();
    private static final Set<UUID> speechReady = new HashSet<>();
    private static long now() { return System.nanoTime() / 1_000_000; }

    @Override public void onInitialize() {
        try { packs = new PackRepository(FabricLoader.getInstance().getConfigDir().resolve("sayitem/languages")); }
        catch (IOException e) { throw new IllegalStateException("Cannot load SayItem packs", e); }
        PayloadTypeRegistry.serverboundPlay().register(Packets.Request.TYPE, Packets.Request.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Packets.Attempt.TYPE, Packets.Attempt.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Packets.SelectLanguage.TYPE, Packets.SelectLanguage.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Packets.SpeechReady.TYPE, Packets.SpeechReady.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Packets.SetAccuracy.TYPE, Packets.SetAccuracy.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Packets.SetTimer.TYPE, Packets.SetTimer.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Packets.SetSharing.TYPE, Packets.SetSharing.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Packets.Progress.TYPE, Packets.Progress.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Packets.SetSharing.TYPE, (packet, context) -> {
            var player = context.player();
            if (!canConfigure(player)) { sendProgress(player); return; }
            if (!progress.setSharing(packet.enabled())) { sendProgress(player); return; }
            if (!saveProgress()) result(player, "", false, false, -1, "Sharing changed. World save pending; retrying.");
            updateProgress(player.level().getServer(), now());
        });
        ServerPlayNetworking.registerGlobalReceiver(Packets.SetAccuracy.TYPE, (packet, context) -> {
            var player = context.player();
            if (!canConfigure(player) || !AccuracyRequirement.valid(packet.percent())) { sendPacks(player); return; }
            var updated = rules.copy(); updated.requiredAccuracy = packet.percent();
            saveRules(player, updated);
        });
        ServerPlayNetworking.registerGlobalReceiver(Packets.SetTimer.TYPE, (packet, context) -> {
            var player = context.player();
            if (!canConfigure(player) || !TimerLimit.valid(packet.seconds())) { sendPacks(player); return; }
            var updated = rules.copy(); updated.timerSeconds = packet.seconds();
            saveRules(player, updated);
        });
        ServerPlayNetworking.registerGlobalReceiver(Packets.SpeechReady.TYPE, (packet, context) -> {
            var id = context.player().getUUID(); run.player(id).resume(now()); speechReady.add(id);
        });
        PayloadTypeRegistry.clientboundPlay().register(Packets.Challenge.TYPE, Packets.Challenge.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Packets.Result.TYPE, Packets.Result.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Packets.PackInfo.TYPE, Packets.PackInfo.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            run.player(handler.player.getUUID()).pause(now()); saveProgress(); sendPacks(handler.player); sendProgress(handler.player);
        });
        ServerPlayNetworking.registerGlobalReceiver(Packets.Request.TYPE, (packet, context) -> {
            var p = context.player(); long time = now();
            if (requests.containsKey(p.getUUID()) && time - requests.get(p.getUUID()) < 250) return;
            requests.put(p.getUUID(), time); sendPacks(p); sendProgress(p); scan(p, time); sendCurrent(p, time, true);
        });
        ServerPlayNetworking.registerGlobalReceiver(Packets.Attempt.TYPE, (packet, context) -> attempt(context.player(), packet));
        ServerPlayNetworking.registerGlobalReceiver(Packets.SelectLanguage.TYPE, (packet, context) -> selectLanguage(context.player(), packet.language()));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try { rules = ServerRules.load(rulesFile()); }
            catch (IOException | RuntimeException e) { throw new IllegalStateException("Cannot load SayItem rules", e); }
            try { packs = new PackRepository(FabricLoader.getInstance().getConfigDir().resolve("sayitem/languages")); }
            catch (IOException e) { throw new IllegalStateException("Cannot reload SayItem packs", e); }
            progressFile = server.getWorldPath(LevelResource.ROOT).resolve("data/sayitem-unlocks.json");
            try { progress = UnlockProgress.load(progressFile); }
            catch (IOException e) { throw new IllegalStateException("Cannot load SayItem world unlock progress", e); }
            var items = new HashSet<String>();
            for (var item : BuiltInRegistries.ITEM) {
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                if (!id.equals("minecraft:air")) items.add(id);
            }
            registeredItems = Set.copyOf(items); progressSaveFailed = false; lastSaveAttempt = 0;
            run = new RunState(progress); lastSent.clear(); requests.clear(); speechReady.clear();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> saveProgress());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            progress = new UnlockProgress(); run = new RunState(progress); progressFile = null; registeredItems = Set.of();
            lastSent.clear(); requests.clear(); speechReady.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            lastSent.remove(handler.player.getUUID()); requests.remove(handler.player.getUUID());
            speechReady.remove(handler.player.getUUID());
            run.player(handler.player.getUUID()).pause(now());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long time = now();
            for (var player : server.getPlayerList().getPlayers()) { scan(player, time); sendCurrent(player, time, false); }
            if (progress.dirty() && time - lastSaveAttempt >= 5000) saveProgress();
        });
        UseItemCallback.EVENT.register((p, w, h) -> p instanceof ServerPlayer sp && !allow(sp, p.getItemInHand(h)) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((p, w, h, hit) -> p instanceof ServerPlayer sp && !allow(sp, p.getItemInHand(h)) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((p, w, h, e, hit) -> p instanceof ServerPlayer sp && !allow(sp, p.getItemInHand(h)) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((p, w, h, e, hit) -> p instanceof ServerPlayer sp && !allow(sp, p.getItemInHand(h)) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((p, w, h, pos, face) -> p instanceof ServerPlayer sp && !allow(sp, p.getItemInHand(h)) ? InteractionResult.FAIL : InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((w, p, pos, state, e) -> !(p instanceof ServerPlayer sp) || allow(sp, p.getMainHandItem()));
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> dispatcher.register(Commands.literal("sayitem")
            .executes(ctx -> {
                var player = ctx.getSource().getPlayerOrException();
                var state = run.player(player.getUUID());
                int unlocked = progress.count(player.getUUID(), registeredItems);
                ctx.getSource().sendSuccess(() -> Component.literal("SayItem · " + state.language + " · " + unlocked
                    + "/" + registeredItems.size() + " unlocked · " + (progress.sharing() ? "shared" : "personal")
                    + " · " + rules.timerSeconds + " seconds · " + rules.requiredAccuracy + "% required"), false); return 1;
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
        run.player(p.getUUID()).selectLanguage(language); sendPacks(p); sendCurrent(p, now(), true); return true;
    }
    private static void sendPacks(ServerPlayer p) {
        var names = new java.util.TreeMap<String, String>();
        for (String id : packs.ids()) names.put(id, packs.get(id).name());
        if (ServerPlayNetworking.canSend(p, Packets.PackInfo.TYPE))
            ServerPlayNetworking.send(p, new Packets.PackInfo(JsonFiles.GSON.toJson(names), run.player(p.getUUID()).language, rules.requiredAccuracy, rules.timerSeconds, canConfigure(p)));
    }
    private static void saveRules(ServerPlayer player, ServerRules updated) {
        try { updated.save(rulesFile()); }
        catch (IOException e) { result(player, "", false, false, -1, "Could not save SayItem settings."); sendPacks(player); return; }
        rules = updated;
        for (var member : player.level().getServer().getPlayerList().getPlayers()) sendPacks(member);
    }
    private static void sendProgress(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, Packets.Progress.TYPE))
            ServerPlayNetworking.send(player, new Packets.Progress(progress.count(player.getUUID(), registeredItems), registeredItems.size(), progress.sharing()));
    }
    private static boolean saveProgress() {
        if (progressFile == null || !progress.dirty()) return true;
        lastSaveAttempt = now();
        try { progress.save(progressFile); progressSaveFailed = false; return true; }
        catch (IOException error) {
            if (!progressSaveFailed) LOGGER.error("Could not save SayItem world unlock progress; retrying", error);
            progressSaveFailed = true; return false;
        }
    }
    private static void updateProgress(net.minecraft.server.MinecraftServer server, long time) {
        for (var member : server.getPlayerList().getPlayers()) {
            skipUnlocked(member, time); sendProgress(member); sendCurrent(member, time, true);
        }
    }
    private static void skipUnlocked(ServerPlayer player, long time) {
        var skipped = run.player(player.getUUID()).skipUnlocked(time);
        if (skipped != null) result(player, skipped.nonce(), true, true, -1, "Unlocked through shared progress!");
    }
    private static boolean canConfigure(ServerPlayer p) {
        var server = p.level().getServer();
        var identity = new net.minecraft.server.players.NameAndId(p.getGameProfile());
        return server.isSingleplayerOwner(identity) || server.getPlayerList().isOp(identity);
    }
    private static String id(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    private static boolean bypass(ServerPlayer p) { return p.isCreative() || p.isSpectator(); }
    private static void scan(ServerPlayer p, long time) {
        if (bypass(p) || !speechReady.contains(p.getUUID())) return;
        var state = run.player(p.getUUID());
        skipUnlocked(p, time);
        var expired = state.expireCurrent(time);
        if (expired != null) {
            removeItem(p, expired.item());
            result(p, expired.nonce(), false, true, -1, "Time's up. Item removed — collect it again to retry.");
        }
        var inventory = p.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) collect(state, inventory.getItem(slot), time);
        for (var slot : EquipmentSlot.values()) collect(state, p.getItemBySlot(slot), time);
        collect(state, p.containerMenu.getCarried(), time);
    }
    private static void collect(RunState.PlayerRun state, ItemStack stack, long time) {
        if (!stack.isEmpty()) state.collect(id(stack), time, rules.timerSeconds);
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
        if (!speechReady.contains(p.getUUID())) return false;
        run.player(p.getUUID()).collect(id(stack), now(), rules.timerSeconds); sendCurrent(p, now(), true); return false;
    }
    private static PackRepository.Phrase phrase(ServerPlayer p, String item) {
        var type = BuiltInRegistries.ITEM.getValue(Identifier.parse(item));
        return packs.phrase(run.player(p.getUUID()).language, item, type.getDefaultInstance().getHoverName().getString());
    }
    private static void sendCurrent(ServerPlayer p, long time, boolean force) {
        if (!speechReady.contains(p.getUUID())) return;
        var state = run.player(p.getUUID()); var current = state.current();
        if (current == null || current.expired(time) || !ServerPlayNetworking.canSend(p, Packets.Challenge.TYPE)) return;
        String signature = current.nonce() + state.language + current.remaining(time) / 1000 + ":" + state.pendingCount();
        if (!force && signature.equals(lastSent.get(p.getUUID()))) return;
        lastSent.put(p.getUUID(), signature);
        var phrase = phrase(p, current.item());
        ServerPlayNetworking.send(p, new Packets.Challenge(current.nonce(), current.item(), state.language,
            phrase.languageName(), phrase.whisperLanguage(), phrase.entry().text(), phrase.entry().reading(), current.remaining(time), state.pendingCount()));
    }
    private static void attempt(ServerPlayer p, Packets.Attempt attempt) {
        if (!speechReady.contains(p.getUUID())) return;
        long time = now(); var state = run.player(p.getUUID());
        var round = state.current(attempt.nonce());
        if (round == null) return;
        if (round.expired(time)) { scan(p, time); return; }
        var entry = phrase(p, round.item()).entry();
        double score = AnswerMatcher.score(attempt.transcript(), entry.text(), entry.aliases());
        int percent = (int) Math.floor(score * 100 + 1e-8);
        if (AccuracyRequirement.accepts(score, rules.requiredAccuracy)) {
            if (!state.unlockCurrent(round.nonce(), time)) return;
            boolean saved = saveProgress();
            result(p, round.nonce(), true, true, percent, "Unlocked! " + percent + "% match." + (saved ? "" : " World save pending; retrying."));
            updateProgress(p.level().getServer(), time);
        } else {
            result(p, round.nonce(), false, false, percent, attempt.transcript().isBlank()
                ? "No speech heard. Try again before time runs out."
                : "Heard: “" + attempt.transcript() + "” · " + percent + "% (need " + rules.requiredAccuracy + "%).");
        }
    }
    private static void result(ServerPlayer p, String nonce, boolean success, boolean finished, int accuracy, String text) {
        if (ServerPlayNetworking.canSend(p, Packets.Result.TYPE)) ServerPlayNetworking.send(p, new Packets.Result(nonce, success, finished, accuracy, text));
    }
}
