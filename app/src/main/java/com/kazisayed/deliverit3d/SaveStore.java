package com.kazisayed.deliverit3d;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Native persistence. Import Chromium's old save files once, without starting a WebView. */
public final class SaveStore {
    private final SharedPreferences prefs;
    public SaveStore(Context context){
        prefs=context.getSharedPreferences("dispatch-native",Context.MODE_PRIVATE);
        if(!prefs.getBoolean("native-import-complete",false))importLegacy(context);
    }
    private void importLegacy(Context context){
        File source=new File(context.getDataDir(),"app_webview/Default/Local Storage/leveldb");
        SharedPreferences.Editor editor=prefs.edit();
        if(source.isDirectory()){
            // Open a private copy: the original save remains available for rollback.
            File copy=new File(context.getCacheDir(),"legacy-save-import");copy.mkdirs();
            try{
                File[] files=source.listFiles();if(files!=null)for(File file:files)if(file.isFile()&&!file.getName().equals("LOCK"))Files.copy(file.toPath(),new File(copy,file.getName()).toPath(),StandardCopyOption.REPLACE_EXISTING);
                System.setProperty("leveldb.mmap","false");
                try(DB db=Iq80DBFactory.factory.open(copy,new Options().createIfMissing(false));DBIterator iterator=db.iterator()){
                    iterator.seekToFirst();while(iterator.hasNext()){Map.Entry<byte[],byte[]> pair=iterator.next();importEntry(pair.getKey(),pair.getValue(),editor);}
                }
            }catch(Exception failure){
                Log.w("Dispatch","Legacy database reader unavailable; reading its write journal",failure);
                File[] logs=source.listFiles((d,n)->n.endsWith(".log"));
                if(logs!=null){java.util.Arrays.sort(logs);for(File log:logs)try{readJournal(Files.readAllBytes(log.toPath()),editor);}catch(Exception ignored){Log.w("Dispatch","Incomplete legacy journal: "+log.getName());}}
            }
        }
        editor.putBoolean("native-import-complete",true).commit();
    }
    private void importEntry(byte[] key,byte[] value,SharedPreferences.Editor editor){
        String name=new String(key,StandardCharsets.ISO_8859_1).replace("\u0000","");
        int start=name.indexOf("deliver3d_");if(start<0)start=name.indexOf("dispatch-volume-");if(start<0)return;
        name=name.substring(start);
        if(value==null||value.length==0)return;
        String text=new String(value,1,value.length-1,value[0]==0?StandardCharsets.UTF_16LE:StandardCharsets.UTF_8);
        editor.putString(name,text);
    }
    private void readJournal(byte[] bytes,SharedPreferences.Editor editor){
        java.io.ByteArrayOutputStream fragments=new java.io.ByteArrayOutputStream();
        for(int offset=0;offset+7<=bytes.length;){
            int left=32768-offset%32768;if(left<7){offset+=left;continue;}
            int size=(bytes[offset+4]&255)|((bytes[offset+5]&255)<<8),type=bytes[offset+6]&255;
            if(size==0&&type==0){offset+=left;continue;}if(offset+7+size>bytes.length)break;
            if(type==1||type==2)fragments.reset();fragments.write(bytes,offset+7,size);offset+=size+7;
            if(type!=1&&type!=4)continue;byte[] r=fragments.toByteArray();if(r.length<12)continue;
            int count=(r[8]&255)|((r[9]&255)<<8)|((r[10]&255)<<16)|((r[11]&255)<<24);int[] p={12};
            for(int i=0;i<count&&p[0]<r.length;i++){
                int op=r[p[0]++]&255;byte[] key=readBytes(r,p);byte[] value=op==1?readBytes(r,p):null;importEntry(key,value,editor);
            }
        }
    }
    private static byte[] readBytes(byte[] data,int[] p){int length=0,shift=0,b;do{b=data[p[0]++]&255;length|=(b&127)<<shift;shift+=7;if(shift>28)throw new IllegalArgumentException();}while((b&128)!=0);if(length<0||p[0]+length>data.length)throw new IllegalArgumentException();byte[] out=java.util.Arrays.copyOfRange(data,p[0],p[0]+length);p[0]+=length;return out;}
    public String text(String key,String fallback){return prefs.getString(key,fallback);}
    public int number(String key,int fallback){try{return Integer.parseInt(text(key,""));}catch(Exception e){return fallback;}}
    public float decimal(String key,float fallback){try{float n=Float.parseFloat(text(key,""));return Float.isFinite(n)?n:fallback;}catch(Exception e){return fallback;}}
    public boolean flag(String key,boolean fallback){return Boolean.parseBoolean(text(key,String.valueOf(fallback)));}
    public void put(String key,Object value){prefs.edit().putString(key,String.valueOf(value)).commit();}
    public int level(){return Math.max(1,number("deliver3d_level",1));}
    public int cash(){return Math.max(0,number("deliver3d_cash",100));}
    public int tickets(){return Math.max(0,number("deliver3d_tickets",45));}
    public Set<String> owned(boolean vehicles){
        Set<String> result=new HashSet<>();result.add(vehicles?"bike":"cap");
        try{JSONArray a=new JSONArray(text(vehicles?"deliver3d_unlocked_veh":"deliver3d_unlocked_hats","[]"));for(int i=0;i<a.length();i++)result.add(a.getString(i));}catch(Exception ignored){}return result;
    }
    public void buy(boolean vehicles,String id,int cash,int tickets){Set<String> owned=owned(vehicles);owned.add(id);prefs.edit().putString(vehicles?"deliver3d_unlocked_veh":"deliver3d_unlocked_hats",new JSONArray(owned).toString()).putString("deliver3d_cash",String.valueOf(cash)).putString("deliver3d_tickets",String.valueOf(tickets)).putString(vehicles?"deliver3d_vehicle":"deliver3d_hat",id).commit();}
    public void bank(int cash,int tickets){prefs.edit().putString("deliver3d_cash",String.valueOf(cash)).putString("deliver3d_tickets",String.valueOf(tickets)).commit();}
    public void reset(){prefs.edit().clear().putBoolean("native-import-complete",true).commit();}

    public static int getCash(android.content.Context ctx){return new SaveStore(ctx).cash();}
    public static String getEquippedVehicle(android.content.Context ctx){return new SaveStore(ctx).text("deliver3d_vehicle","bike");}
    public static void setEquippedVehicle(android.content.Context ctx,String id){new SaveStore(ctx).put("deliver3d_vehicle",id);}
    public static long getBestScore(android.content.Context ctx){try{return Long.parseLong(new SaveStore(ctx).text("nightrider_best_score","0"));}catch(Exception e){return 0L;}}
    public static void setBestScore(android.content.Context ctx,long score){new SaveStore(ctx).put("nightrider_best_score",score);}

    public int speedUpgrade(String vehicleId) {
        return Math.max(0, Math.min(3, number("upgrade_speed_" + (vehicleId != null ? vehicleId : "bike"), 0)));
    }

    public int capUpgrade(String vehicleId) {
        return Math.max(0, Math.min(3, number("upgrade_cap_" + (vehicleId != null ? vehicleId : "bike"), 0)));
    }

    public boolean upgradeSpeedPart(String vehicleId, int cost) {
        int curTier = speedUpgrade(vehicleId);
        if (curTier >= 3) return false;
        if (cash() < cost) return false;
        int remaining = cash() - cost;
        put("deliver3d_cash", remaining);
        put("upgrade_speed_" + vehicleId, curTier + 1);
        return true;
    }

    public boolean upgradeCapPart(String vehicleId, int cost) {
        int curTier = capUpgrade(vehicleId);
        if (curTier >= 3) return false;
        if (cash() < cost) return false;
        int remaining = cash() - cost;
        put("deliver3d_cash", remaining);
        put("upgrade_cap_" + vehicleId, curTier + 1);
        return true;
    }

    public static int getSpeedUpgradeLevel(android.content.Context ctx, String vehicleId) {
        return new SaveStore(ctx).speedUpgrade(vehicleId);
    }

    public static int getCapacityUpgradeLevel(android.content.Context ctx, String vehicleId) {
        return new SaveStore(ctx).capUpgrade(vehicleId);
    }

    public static boolean isVehicleOwned(android.content.Context ctx, String vehicleId) {
        if (vehicleId == null || vehicleId.equalsIgnoreCase("bike")) return true;
        return new SaveStore(ctx).owned(true).contains(vehicleId);
    }

    public static boolean buyVehicle(android.content.Context ctx, String vehicleId, int price) {
        SaveStore s = new SaveStore(ctx);
        if (s.owned(true).contains(vehicleId)) return true;
        if (s.cash() < price) return false;
        int remaining = s.cash() - price;
        s.buy(true, vehicleId, remaining, s.tickets());
        try {
            UserManager.get().loadLocal(ctx);
            UserManager.get().spendCash(ctx, price);
        } catch (Throwable ignored) {}
        return true;
    }

    public static boolean upgradeVehicleSpeed(android.content.Context ctx, String vehicleId, int cost) {
        SaveStore s = new SaveStore(ctx);
        boolean success = s.upgradeSpeedPart(vehicleId, cost);
        if (success) {
            try {
                UserManager.get().loadLocal(ctx);
                UserManager.get().spendCash(ctx, cost);
            } catch (Throwable ignored) {}
        }
        return success;
    }

    public static boolean upgradeVehicleCapacity(android.content.Context ctx, String vehicleId, int cost) {
        SaveStore s = new SaveStore(ctx);
        boolean success = s.upgradeCapPart(vehicleId, cost);
        if (success) {
            try {
                UserManager.get().loadLocal(ctx);
                UserManager.get().spendCash(ctx, cost);
            } catch (Throwable ignored) {}
        }
        return success;
    }

    public int utilUpgrade(String vehicleId) {
        return Math.max(0, Math.min(3, number("upgrade_util_" + (vehicleId != null ? vehicleId : "bike"), 0)));
    }

    public boolean upgradeUtilPart(String vehicleId, int cost) {
        int curTier = utilUpgrade(vehicleId);
        if (curTier >= 3) return false;
        if (cash() < cost) return false;
        int remaining = cash() - cost;
        put("deliver3d_cash", remaining);
        put("upgrade_util_" + vehicleId, curTier + 1);
        return true;
    }

    public static int getUtilityUpgradeLevel(android.content.Context ctx, String vehicleId) {
        return new SaveStore(ctx).utilUpgrade(vehicleId);
    }

    public static boolean upgradeVehicleUtility(android.content.Context ctx, String vehicleId, int cost) {
        SaveStore s = new SaveStore(ctx);
        boolean success = s.upgradeUtilPart(vehicleId, cost);
        if (success) {
            try {
                UserManager.get().loadLocal(ctx);
                UserManager.get().spendCash(ctx, cost);
            } catch (Throwable ignored) {}
        }
        return success;
    }
    public static void cacheContracts(android.content.Context ctx,java.util.List<com.kazisayed.deliverit3d.model.Contract> list){
        if(list==null)return;
        try{
            org.json.JSONArray arr=new org.json.JSONArray();
            for(com.kazisayed.deliverit3d.model.Contract c:list){
                org.json.JSONObject obj=new org.json.JSONObject();
                obj.put("id",c.getId());obj.put("route",c.getRoute());obj.put("payout",c.getPayout());
                obj.put("time",c.getTimeLimitSeconds());obj.put("cargo",c.getCargo());obj.put("diff",c.getDifficulty());
                arr.put(obj);
            }
            new SaveStore(ctx).put("cached_contracts",arr.toString());
        }catch(Exception ignored){}
    }
    public static java.util.List<com.kazisayed.deliverit3d.model.Contract> getCachedContracts(android.content.Context ctx){
        java.util.List<com.kazisayed.deliverit3d.model.Contract> list=new java.util.ArrayList<>();
        String raw=new SaveStore(ctx).text("cached_contracts",null);
        if(raw!=null){
            try{
                org.json.JSONArray arr=new org.json.JSONArray(raw);
                for(int i=0;i<arr.length();i++){
                    org.json.JSONObject o=arr.getJSONObject(i);
                    list.add(new com.kazisayed.deliverit3d.model.Contract(
                        o.optString("id"),o.optString("route"),o.optInt("payout"),
                        o.optInt("time"),o.optString("cargo"),o.optInt("diff")
                    ));
                }
            }catch(Exception ignored){}
        }
        return list;
    }
}
