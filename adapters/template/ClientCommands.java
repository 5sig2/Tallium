package dev.sig.tallium.adapter;

import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import dev.sig.tallium.config.Config;
import dev.sig.tallium.inventory.InventorySnapshot;
import java.util.*;


public final class ClientCommands {
    public static void register(){
        var connection=Minecraft.getInstance().getConnection();if(connection==null)return;
        register(connection.getCommands());
    }
    private static <S> void register(CommandDispatcher<S> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<S>literal("tallium").executes(c->1)
            .then(LiteralArgumentBuilder.<S>literal("overview").executes(c->1))
            .then(LiteralArgumentBuilder.<S>literal("check")
                .then(RequiredArgumentBuilder.<S,String>argument("player",StringArgumentType.word())
                    .suggests((c,b)->SharedSuggestionProvider.suggest(TalliumClient.roster.values().stream().map(TalliumClient.SeenPlayer::name),b)).executes(c->1))));
    }
    public static boolean execute(String command){
        String[] words=command.strip().split("\\s+");if(words.length==0||!words[0].equalsIgnoreCase("tallium"))return false;
        var mc=Minecraft.getInstance();if(mc.player==null)return true;
        List<Component> lines;
        if(words.length==2&&words[1].equalsIgnoreCase("overview"))lines=overview();
        else if(words.length==3&&words[1].equalsIgnoreCase("check"))lines=check(words[2]);
        else lines=List.of(Component.literal("Tallium · /tallium overview · /tallium check <player>"));
        for(Component line:lines)mc.gui.getChat().addMessage(line);return true;
    }
    public static List<Component> overview(){
        List<Component> lines=new ArrayList<>();lines.add(Component.literal("Tallium · Loaded players · Used counts since joining"));
        TalliumClient.roster.values().stream().filter(TalliumClient.SeenPlayer::present).sorted(Comparator.comparing(TalliumClient.SeenPlayer::name,String.CASE_INSENSITIVE_ORDER)).forEach(player->lines.addAll(report(player,true)));
        if(lines.size()==1)lines.add(Component.literal("No players are currently loaded."));return List.copyOf(lines);
    }
    public static List<Component> check(String name){
        return TalliumClient.roster.values().stream().filter(p->p.name().equalsIgnoreCase(name)).findFirst().map(p->report(p,false))
            .orElse(List.of(Component.literal("Tallium · No record for "+name+". Players must enter your render distance first.")));
    }
    private static List<Component> report(TalliumClient.SeenPlayer player,boolean compact){
        var mc=Minecraft.getInstance();boolean own=player.uuid().equals(mc.player.getUUID());
        var entity=mc.level.players().stream().filter(p->p.getUUID().equals(player.uuid())).findFirst().orElse(null);
        List<InventorySnapshot.Slot> hands=new ArrayList<>();
        if(entity!=null){int n=0;for(var stack:List.of(entity.getMainHandItem(),entity.getOffhandItem()))if(!stack.isEmpty())hands.add(new InventorySnapshot.Slot(n++,TalliumClient.identity(stack),stack.getCount(),false,true));}
        var visible=new InventorySnapshot(hands);List<Component> lines=new ArrayList<>();List<String> entries=new ArrayList<>();
        for(Config.Definition d:TalliumClient.config.active().definitions){if(!d.enabled||d.unavailable)continue;
            String remaining=own?Long.toString(TalliumClient.inventory.total(List.of(d.selector),false)):"Unknown";
            String entry=d.label+": "+remaining+" remaining / "+TalliumClient.usage(player.uuid(),d).text()+" used";
            if(!own&&entity!=null)entry+=" / "+visible.total(List.of(d.selector),false)+" in hands";
            entries.add(entry);
        }
        String heading=player.name()+(player.present()?"":" (last seen "+TalliumClient.lastSeenSeconds(player)+"s ago)");
        if(compact)lines.add(Component.literal(heading+" · "+String.join("; ",entries)));
        else{lines.add(Component.literal("Tallium · "+heading));entries.forEach(e->lines.add(Component.literal(e)));}
        if(!compact&&!own)lines.add(Component.literal("Other players' full inventories are not sent to your client."));return List.copyOf(lines);
    }
}
