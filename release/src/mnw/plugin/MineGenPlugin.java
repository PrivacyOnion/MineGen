package mnw.plugin;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.*;

/** Public distribution bootstrap. The tested terrain engine is packaged unchanged. */
public final class MineGenPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private World managed;
    @Override public void onEnable() {
        saveDefaultConfig();
        getCommand("minegen").setExecutor(this);
        getCommand("minegen").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this,this);
        getServer().getScheduler().runTask(this,()->{
            try {startWorld();} catch(RuntimeException e) {
                getLogger().log(java.util.logging.Level.SEVERE,"MineGen ne peut pas activer son monde. Verifiez la configuration.",e);
            }
        });
    }
    private void startWorld() {
        if(!getConfig().getBoolean("world.enabled",true))return;
        String name=getConfig().getString("world.name","minegen");
        if(name==null||!name.matches("[A-Za-z0-9_-]{1,48}")) {
            getLogger().severe("world.name invalide : lettres, chiffres, _ et - uniquement.");return;
        }
        Difficulty difficulty;
        try {difficulty=Difficulty.valueOf(getConfig().getString("world.difficulty","NORMAL").toUpperCase(Locale.ROOT));}
        catch(IllegalArgumentException e){getLogger().severe("world.difficulty invalide : PEACEFUL, EASY, NORMAL ou HARD.");return;}
        World existing=Bukkit.getWorld(name);
        if(existing!=null) {
            if(!(existing.getGenerator() instanceof MineGenChunkGenerator)) {
                getLogger().severe("Le monde "+name+" utilise un autre generateur. Choisissez un nouveau nom.");return;
            }
            managed=existing;
        } else {
            // Never silently reopen a foreign world with a different generator.
            File folder=new File(getServer().getWorldContainer(),name);
            String owned=getConfig().getString("internal.created-world","");
            if(folder.exists()&&!name.equals(owned)) {
                getLogger().severe("Dossier existant non gere par MineGen : "+name+". Choisissez un nom libre.");return;
            }
            String seed=getConfig().getString("world.seed","");
            long value;
            if(seed==null||seed.isBlank()) {
                value=new java.security.SecureRandom().nextLong();
                getConfig().set("world.seed",Long.toString(value));saveConfig();
            } else {
                try {value=Long.parseLong(seed);}catch(NumberFormatException e){value=seed.hashCode();}
            }
            getConfig().set("internal.created-world",name);saveConfig();
            managed=new WorldCreator(name).environment(World.Environment.NORMAL).seed(value)
                    .generateStructures(getConfig().getBoolean("world.structures",true))
                    .generator(new MineGenChunkGenerator()).createWorld();
        }
        if(managed==null){getLogger().severe("Creation du monde impossible.");return;}
        managed.setDifficulty(difficulty);
        getLogger().info("MineGen pret : "+managed.getName()+" ; /minegen tp pour visiter.");
    }
    @Override public ChunkGenerator getDefaultWorldGenerator(String name,String id){return new MineGenChunkGenerator();}
    @EventHandler public void worldInit(WorldInitEvent event){
        if(getConfig().getBoolean("performance.fast-biomes",true)
                &&event.getWorld().getGenerator() instanceof MineGenChunkGenerator generator)
            generator.installFastBiomes(event.getWorld(),getLogger());
    }
    @EventHandler public void join(PlayerJoinEvent e){
        if(!e.getPlayer().hasPlayedBefore()&&getConfig().getBoolean("world.first-join",false))
            getServer().getScheduler().runTask(this,()->teleport(e.getPlayer()));
    }
    private void teleport(Player p){
        if(managed==null){p.sendMessage("MineGen : monde indisponible ; consultez la console.");return;}
        p.teleportAsync(managed.getSpawnLocation()).thenAccept(ok->{
            if(!ok)getLogger().warning("Teleportation annulee pour "+p.getName());
        }).exceptionally(error->{
            getLogger().log(java.util.logging.Level.WARNING,"Teleportation impossible pour "+p.getName(),error);
            return null;
        });
    }
    @Override public boolean onCommand(CommandSender s,Command c,String label,String[] args){
        String sub=args.length==0?"status":args[0].toLowerCase(Locale.ROOT);
        if(sub.equals("tp")){
            if(!s.hasPermission("minegen.teleport")){s.sendMessage("Permission refusee.");return true;}
            if(s instanceof Player p)teleport(p);else s.sendMessage("Commande reservee aux joueurs.");
        }else if(sub.equals("status")){
            s.sendMessage("MineGen 1.0.0 : "+(managed==null?"monde non actif":managed.getName()+" actif"));
        }else s.sendMessage("/minegen status | /minegen tp — config.yml, puis redemarrage pour appliquer.");
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String label,String[] args){
        if(args.length!=1)return List.of();
        return List.of("status","tp").stream().filter(x->!x.equals("tp")||s.hasPermission("minegen.teleport"))
                .filter(x->x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
