package com.tntcraft.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class Cmds {
    private Cmds() {}

    private static final SuggestionProvider<CommandSourceStack> HOME_NAMES = (ctx, b) -> {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        return SharedSuggestionProvider.suggest(
                Data.player(p.getUUID(), p.getName().getString()).homes.keySet(), b);
    };

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        // ---- Evler ----
        d.register(Commands.literal("sethome")
                .executes(c -> setHome(c, "home"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> setHome(c, StringArgumentType.getString(c, "name")))));

        d.register(Commands.literal("home")
                .executes(c -> goHome(c, "home"))
                .then(Commands.argument("name", StringArgumentType.word()).suggests(HOME_NAMES)
                        .executes(c -> goHome(c, StringArgumentType.getString(c, "name")))));

        d.register(Commands.literal("delhome")
                .then(Commands.argument("name", StringArgumentType.word()).suggests(HOME_NAMES)
                        .executes(c -> delHome(c, StringArgumentType.getString(c, "name")))));

        d.register(Commands.literal("homes").executes(Cmds::listHomes));

        // ---- Ekonomi ----
        LiteralCommandNode<CommandSourceStack> bal = d.register(Commands.literal("balance").executes(Cmds::balance));
        d.register(Commands.literal("bal").redirect(bal));

        d.register(Commands.literal("pay")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(Cmds::pay))));

        d.register(Commands.literal("daily").executes(Cmds::daily));
        d.register(Commands.literal("baltop").executes(Cmds::baltop));

        d.register(Commands.literal("eco")
                .then(Commands.literal("give").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(c -> eco(c, "give")))))
                .then(Commands.literal("take").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(c -> eco(c, "take")))))
                .then(Commands.literal("set").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                .executes(c -> eco(c, "set"))))));

        // ---- Satis ----
        d.register(Commands.literal("sell").executes(Cmds::sell));
        d.register(Commands.literal("prices").executes(Cmds::prices));
    }

    // ---------- yardimcilar ----------
    private static String cur() {
        return Data.settings().currency;
    }

    private static void say(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal("[TNTCraft] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(msg).withStyle(ChatFormatting.WHITE)), false);
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
        return 0;
    }

    private static Data.PlayerData pd(ServerPlayer p) {
        return Data.player(p.getUUID(), p.getName().getString());
    }

    /** "ResourceKey[minecraft:dimension / minecraft:overworld]" -> "minecraft:overworld" */
    private static String dimOf(ServerLevel level) {
        String s = level.dimension().toString();
        int i = s.lastIndexOf("/ ");
        int j = s.lastIndexOf(']');
        return (i >= 0 && j > i) ? s.substring(i + 2, j).trim() : s;
    }

    private static boolean isAdmin(CommandSourceStack src) {
        ServerPlayer sp = src.getPlayer();
        if (sp == null) return true; // konsol
        String n = sp.getName().getString();
        for (String a : Data.settings().admins) if (a.equalsIgnoreCase(n)) return true;
        return false;
    }

    // ---------- evler ----------
    private static int setHome(CommandContext<CommandSourceStack> c, String raw) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Data.PlayerData data = pd(p);
        String name = raw.toLowerCase(Locale.ROOT);
        if (!data.homes.containsKey(name) && data.homes.size() >= Data.settings().maxHomes)
            return fail(c, "En fazla " + Data.settings().maxHomes + " ev kurabilirsin. /delhome ile birini sil.");
        Data.Home h = new Data.Home();
        h.dim = dimOf((ServerLevel) p.level());
        h.x = p.getX(); h.y = p.getY(); h.z = p.getZ();
        h.yaw = p.getYRot(); h.pitch = p.getXRot();
        data.homes.put(name, h);
        Data.save();
        say(c, "Ev kaydedildi: " + name);
        return 1;
    }

    private static int goHome(CommandContext<CommandSourceStack> c, String raw) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String name = raw.toLowerCase(Locale.ROOT);
        Data.Home h = pd(p).homes.get(name);
        if (h == null) return fail(c, "Böyle bir evin yok: " + name + " (/homes ile bak)");
        ServerLevel target = null;
        for (ServerLevel l : c.getSource().getServer().getAllLevels()) {
            if (dimOf(l).equals(h.dim)) { target = l; break; }
        }
        if (target == null) return fail(c, "Evin bulunduğu boyut şu an yüklü değil.");
        p.teleportTo(target, h.x, h.y, h.z, Set.of(), h.yaw, h.pitch, true);
        say(c, "Eve ışınlandın: " + name);
        return 1;
    }

    private static int delHome(CommandContext<CommandSourceStack> c, String raw) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String name = raw.toLowerCase(Locale.ROOT);
        if (pd(p).homes.remove(name) == null) return fail(c, "Böyle bir evin yok: " + name);
        Data.save();
        say(c, "Ev silindi: " + name);
        return 1;
    }

    private static int listHomes(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Set<String> names = pd(p).homes.keySet();
        say(c, names.isEmpty() ? "Hiç evin yok. /sethome ile kur." : "Evlerin: " + String.join(", ", names));
        return 1;
    }

    // ---------- ekonomi ----------
    private static int balance(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        say(c, "Bakiyen: " + pd(p).balance + " " + cur());
        return 1;
    }

    private static int pay(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer from = c.getSource().getPlayerOrException();
        ServerPlayer to = EntityArgument.getPlayer(c, "player");
        long amount = LongArgumentType.getLong(c, "amount");
        if (from.getUUID().equals(to.getUUID())) return fail(c, "Kendine para gönderemezsin.");
        Data.PlayerData a = pd(from), b = pd(to);
        if (a.balance < amount) return fail(c, "Yeterli paran yok. Bakiyen: " + a.balance + " " + cur());
        a.balance -= amount; b.balance += amount;
        Data.save();
        say(c, to.getName().getString() + " oyuncusuna " + amount + " " + cur() + " gönderdin.");
        to.sendSystemMessage(Component.literal("[TNTCraft] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(from.getName().getString() + " sana " + amount + " " + cur() + " gönderdi.")
                        .withStyle(ChatFormatting.WHITE)));
        return 1;
    }

    private static int daily(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Data.PlayerData data = pd(p);
        long now = System.currentTimeMillis(), day = 24L * 60 * 60 * 1000;
        if (now - data.lastDaily < day) {
            long left = (day - (now - data.lastDaily)) / 60000;
            return fail(c, "Günlük ödülü zaten aldın. Kalan: " + (left / 60) + " sa " + (left % 60) + " dk");
        }
        data.lastDaily = now;
        data.balance += Data.settings().dailyReward;
        Data.save();
        say(c, "Günlük ödül: +" + Data.settings().dailyReward + " " + cur() + ". Bakiyen: " + data.balance);
        return 1;
    }

    private static int baltop(CommandContext<CommandSourceStack> c) {
        List<Data.PlayerData> all = new ArrayList<>(Data.players().values());
        all.sort((x, y) -> Long.compare(y.balance, x.balance));
        StringBuilder sb = new StringBuilder("En zenginler:");
        for (int i = 0; i < Math.min(10, all.size()); i++)
            sb.append("\n").append(i + 1).append(". ").append(all.get(i).name).append(" - ").append(all.get(i).balance).append(" ").append(cur());
        say(c, sb.toString());
        return 1;
    }

    private static int eco(CommandContext<CommandSourceStack> c, String mode) throws CommandSyntaxException {
        if (!isAdmin(c.getSource())) return fail(c, "Bu komutu sadece yöneticiler kullanabilir.");
        ServerPlayer t = EntityArgument.getPlayer(c, "player");
        long amount = LongArgumentType.getLong(c, "amount");
        Data.PlayerData data = pd(t);
        switch (mode) {
            case "give" -> data.balance += amount;
            case "take" -> data.balance = Math.max(0, data.balance - amount);
            default -> data.balance = amount;
        }
        Data.save();
        say(c, t.getName().getString() + " yeni bakiye: " + data.balance + " " + cur());
        return 1;
    }

    // ---------- satis ----------
    private static int sell(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ItemStack st = p.getMainHandItem();
        if (st.isEmpty()) return fail(c, "Elinde satılacak bir eşya yok.");
        String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
        Long price = Data.settings().sellPrices.get(id);
        if (price == null) return fail(c, "Bu eşya satılamaz. Satılabilenler için /prices");
        int n = st.getCount();
        long total = price * n;
        st.shrink(n);
        Data.PlayerData data = pd(p);
        data.balance += total;
        Data.save();
        say(c, n + " adet satıldı: +" + total + " " + cur() + ". Bakiyen: " + data.balance);
        return 1;
    }

    private static int prices(CommandContext<CommandSourceStack> c) {
        StringBuilder sb = new StringBuilder("Satış fiyatları (adet başına):");
        for (Map.Entry<String, Long> e : Data.settings().sellPrices.entrySet())
            sb.append("\n").append(e.getKey().replace("minecraft:", "")).append(" = ").append(e.getValue()).append(" ").append(cur());
        say(c, sb.toString());
        return 1;
    }
}
