package com.selteck.ascension;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.*;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.view.MotionEvent;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

public class MainActivity extends Activity {
    private GameView game;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(7, 8, 18));
        getWindow().setNavigationBarColor(Color.rgb(7, 8, 18));
        game = new GameView();
        setContentView(game);
    }
    @Override protected void onPause() { super.onPause(); if (game != null) game.pauseAudio(); }
    @Override protected void onResume() { super.onResume(); if (game != null) game.resumeAudio(); }
    @Override protected void onDestroy() { if (game != null) game.releaseAudio(); super.onDestroy(); }

    final class GameView extends View {
        Canvas c; Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); float sx, sy;
        SharedPreferences prefs; Random rng = new Random();
        int tab=0, level, xp, gold, hp, maxHp, attack, defense, kills, skillPoints, mana, maxMana, dailyClaimed;
        int strength, vitality, focus, crit, potions, shards, bossKills, questClaimed, eventClaimed;
        int weaponTier, armorTier, relicTier, combo, eventProgress, lastHit;
        int weaponId, armorId, weaponPower, armorPower, weaponRarity, armorRarity, casesOpened, stageWins;
        float heroX=112, enemyX=270, heroJump=0; long jumpUntil=0, invulnerableUntil=0, nextEnemyAttack=0, lastStep=0;
        String lootNotice="Explore the Shattered Realm", lastLoot="No loot yet"; long lootNoticeUntil=0;
        int enemyHp, enemyMax, enemyType, region, flashTicks, hitTicks, shakeTicks;
        boolean boss, eventActive, audioOn=true, showBag=false, skillBurst=false;
        String enemyName="RIFT WRAITH";
        long eventSeed;
        SoundPool sounds; int sHit, sCrit, sBoss, sLevel, sBuy, sHeal, sWin, sClick, sSkill;
        long lastAttackAt=0;
        GameView() {
            super(MainActivity.this);
            prefs=getSharedPreferences("ascension_save_v2",MODE_PRIVATE);
            level=prefs.getInt("level",1); xp=prefs.getInt("xp",0); gold=prefs.getInt("gold",180);
            maxHp=prefs.getInt("maxHp",140); hp=prefs.getInt("hp",maxHp); maxMana=prefs.getInt("maxMana",100); mana=prefs.getInt("mana",maxMana); dailyClaimed=prefs.getInt("dailyClaimed",0); attack=prefs.getInt("attack",20);
            defense=prefs.getInt("defense",6); kills=prefs.getInt("kills",0); skillPoints=prefs.getInt("skillPoints",0);
            strength=prefs.getInt("strength",0); vitality=prefs.getInt("vitality",0); focus=prefs.getInt("focus",0);
            crit=prefs.getInt("crit",5); potions=prefs.getInt("potions",2); shards=prefs.getInt("shards",0);
            bossKills=prefs.getInt("bossKills",0); questClaimed=prefs.getInt("questClaimed",0);
            eventClaimed=prefs.getInt("eventClaimed",0); weaponTier=prefs.getInt("weaponTier",1);
            armorTier=prefs.getInt("armorTier",0); relicTier=prefs.getInt("relicTier",0);
            region=prefs.getInt("region",0); eventProgress=prefs.getInt("eventProgress",0);
            weaponId=prefs.getInt("weaponId",0); armorId=prefs.getInt("armorId",0); weaponPower=prefs.getInt("weaponPower",0); armorPower=prefs.getInt("armorPower",0); weaponRarity=prefs.getInt("weaponRarity",0); armorRarity=prefs.getInt("armorRarity",0); casesOpened=prefs.getInt("casesOpened",0); stageWins=prefs.getInt("stageWins",0);
            enemyType=prefs.getInt("enemyType",0); boss=prefs.getBoolean("boss",false);
            enemyMax=prefs.getInt("enemyMax",90+level*9); enemyHp=prefs.getInt("enemyHp",enemyMax);
            if(hp<1) hp=maxHp;
            if(enemyHp<1) enemyHp=enemyMax;
            enemyName=boss?"THE HOLLOW KING":enemyLabel(enemyType);
            eventSeed=prefs.getLong("eventSeed",System.currentTimeMillis()/86400000L);
            long today=System.currentTimeMillis()/86400000L; if(eventSeed!=today){eventSeed=today;eventProgress=0;eventClaimed=0;dailyClaimed=0;}
            mana=Math.max(0,Math.min(maxMana,mana));
            setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            initSounds();
            setContentDescription("PROJECT ASCENSION dark fantasy role-playing game");
            postInvalidateDelayed(40);
        }
        void initSounds() {
            try {
                AudioAttributes aa=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
                sounds=new SoundPool.Builder().setMaxStreams(6).setAudioAttributes(aa).build();
                sHit=loadTone("hit",105,0.12,0.25); sCrit=loadTone("crit",190,0.18,0.35);
                sBoss=loadTone("boss",62,0.42,0.6); sLevel=loadTone("level",440,0.34,0.4);
                sBuy=loadTone("buy",620,0.10,0.22); sHeal=loadTone("heal",520,0.22,0.25);
                sWin=loadTone("win",330,0.45,0.45); sClick=loadTone("click",260,0.055,0.12);
                sSkill=loadTone("skill",780,0.2,0.3);
            } catch(Exception ignored) { sounds=null; }
        }
        int loadTone(String name,double freq,double seconds,double volume) throws Exception {
            int rate=22050, n=(int)(rate*seconds);
            ByteBuffer b=ByteBuffer.allocate(44+n*2).order(ByteOrder.LITTLE_ENDIAN);
            b.put(new byte[]{'R','I','F','F'}); b.putInt(36+n*2); b.put(new byte[]{'W','A','V','E','f','m','t',' '});
            b.putInt(16); b.putShort((short)1); b.putShort((short)1); b.putInt(rate); b.putInt(rate*2); b.putShort((short)2); b.putShort((short)16);
            b.put(new byte[]{'d','a','t','a'}); b.putInt(n*2);
            for(int i=0;i<n;i++) {
                double t=i/(double)rate, env=Math.min(1,t*45)*Math.pow(Math.max(0,1-t/seconds),1.8);
                double wobble=1+0.035*Math.sin(t*27);
                double wave=Math.sin(2*Math.PI*freq*wobble*t);
                if(name.equals("hit")||name.equals("crit")) wave=wave*0.55+(rng.nextDouble()*2-1)*0.45;
                if(name.equals("boss")) wave=Math.sin(2*Math.PI*freq*t)+0.3*Math.sin(2*Math.PI*freq*0.5*t);
                short v=(short)(Math.max(-1,Math.min(1,wave*env*volume))*32767);
                b.putShort(v);
            }
            File f=new File(getCacheDir(),"asc_"+name+".wav");
            FileOutputStream out=new FileOutputStream(f); out.write(b.array()); out.close();
            return sounds.load(f.getAbsolutePath(),1);
        }
        void play(int id) { if(audioOn&&sounds!=null&&id!=0) try { sounds.play(id,1,1,1,0,0.9f+rng.nextFloat()*0.22f); } catch(Exception ignored){} }
        void pauseAudio(){if(sounds!=null)sounds.autoPause();}
        void resumeAudio(){if(sounds!=null)sounds.autoResume();}
        void releaseAudio(){if(sounds!=null){sounds.release();sounds=null;}}
        int color(String h){return Color.parseColor(h);}
        void paint(int co){p.reset();p.setAntiAlias(true);p.setColor(co);}
        void rect(float l,float t,float r,float b,int co,float rad){paint(co);c.drawRoundRect(l,t,r,b,rad,rad,p);}
        void outline(float l,float t,float r,float b,int co,float rad,float sw){paint(co);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(sw);c.drawRoundRect(l,t,r,b,rad,rad,p);p.setStyle(Paint.Style.FILL);}
        void gradient(float l,float t,float r,float b,int a,int z,float rad){paint(Color.WHITE);p.setShader(new LinearGradient(l,t,r,b,a,z,Shader.TileMode.CLAMP));c.drawRoundRect(l,t,r,b,rad,rad,p);p.setShader(null);}
        void line(float x,float y,float x2,float y2,int co,float sw){paint(co);p.setStrokeWidth(sw);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x,y,x2,y2,p);}
        void txt(String s,float x,float y,float size,int co,boolean bold){paint(co);p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));c.drawText(s,x,y,p);}
        void center(String s,float x,float y,float size,int co,boolean bold){paint(co);p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));c.drawText(s,x-p.measureText(s)/2,y,p);}
        void bar(float x,float y,float w,float h,float v,int bg,int fg){rect(x,y,x+w,y+h,bg,h/2);if(v>0)rect(x,y,x+Math.max(h,Math.min(1,v)*w),y+h,fg,h/2);}
        void glow(float x,float y,float radius,int co,float blur){paint(co);p.setMaskFilter(new BlurMaskFilter(blur,BlurMaskFilter.Blur.NORMAL));c.drawCircle(x,y,radius,p);p.setMaskFilter(null);}
        void save(){prefs.edit().putInt("level",level).putInt("xp",xp).putInt("gold",gold).putInt("hp",hp).putInt("maxHp",maxHp).putInt("mana",mana).putInt("maxMana",maxMana).putInt("dailyClaimed",dailyClaimed)
            .putInt("attack",attack).putInt("defense",defense).putInt("kills",kills).putInt("skillPoints",skillPoints)
            .putInt("strength",strength).putInt("vitality",vitality).putInt("focus",focus).putInt("crit",crit).putInt("potions",potions)
            .putInt("shards",shards).putInt("bossKills",bossKills).putInt("questClaimed",questClaimed).putInt("eventClaimed",eventClaimed)
            .putInt("weaponTier",weaponTier).putInt("armorTier",armorTier).putInt("relicTier",relicTier).putInt("region",region)
            .putInt("eventProgress",eventProgress).putInt("weaponId",weaponId).putInt("armorId",armorId).putInt("weaponPower",weaponPower).putInt("armorPower",armorPower).putInt("weaponRarity",weaponRarity).putInt("armorRarity",armorRarity).putInt("casesOpened",casesOpened).putInt("stageWins",stageWins).putInt("enemyType",enemyType).putBoolean("boss",boss).putInt("enemyMax",enemyMax).putInt("enemyHp",enemyHp)
            .putLong("eventSeed",eventSeed).apply();}
        int xpNeed(){return 190+level*78+level*level*3;}
        int actualAttack(){return attack+weaponTier*3+strength*4+relicTier*2+weaponPower;}
        int actualDefense(){return defense+armorTier*3+vitality*2+relicTier+armorPower;}
        int actualMaxHp(){return maxHp+armorTier*15+vitality*18+armorPower*3;}
        int upgradeCost(){return 55+weaponTier*62+level*12;}
        int levelCost(){return 2+level/3;}
        String enemyLabel(int i){String[][] a={{"RIFT WRAITH","VEIL STALKER","GRAVE MITE","DUSK REVENANT","ABYSS KNIGHT","RIFT BANSHEE","VOID HOUND"},{"ASHEN STALKER","CINDER WOLF","EMBER GOLEM","SCORCH WRAITH","ASHEN BRUTE","PYRE WITCH","CHAR HOUND"},{"FROST HOUND","GLACIER WRAITH","ICEBOUND KNIGHT","SNOW WIDOW","FROST BRUTE","CRYSTAL STALKER","RIME GHOUL"},{"DROWNED GUARD","TIDE WRAITH","SUNKEN KNIGHT","DEEP MAW","CORAL WITCH","ABYSSAL EEL","SALT GOLEM"},{"STAR HUNTER","ASTRAL WOLF","COMET WRAITH","FALLEN ORACLE","STARFORGED KNIGHT","NOVA WITCH","COSMIC MAW"},{"HOLLOW KNIGHT","VOID LEECH","NULL STALKER","REALM EATER","CROWN WRAITH","OBLIVION BRUTE","THE UNMAKER"}};return a[Math.floorMod(region,a.length)][Math.floorMod(i, a[Math.floorMod(region,a.length)].length)];}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);c=canvas;sx=getWidth()/390f;sy=getHeight()/844f;c.save();c.scale(sx,sy);
            paint(color("#080914"));c.drawRect(0,0,390,844,p);
            p.setShader(new LinearGradient(0,0,340,844,color("#19152F"),color("#080914"),Shader.TileMode.CLAMP));c.drawRect(0,0,390,844,p);p.setShader(null);
            glow(315,230,65,color("#40316E"),55);glow(40,470,55,color("#222C62"),42);
            for(int i=0;i<52;i++){float xx=(i*73+17)%390, yy=(i*131+19)%710;paint(Color.argb(80+(i%5)*28,173,176,255));c.drawCircle(xx,yy,0.5f+(i%3)*0.4f,p);}
            header();
            if(tab==0)battle();else if(tab==1)hero();else if(tab==2)skills();else if(tab==3)quests();else world();
            nav(); if(flashTicks>0){rect(0,0,390,735,Color.argb(Math.min(90,flashTicks*12),210,85,140),0);flashTicks--;}
            if(hitTicks>0)hitTicks--; c.restore();postInvalidateDelayed(45);
        }
        void header(){
            txt("P R O J E C T   /   A S C E N S I O N",18,28,9,color("#AAA1D2"),true);
            txt("✦ "+gold,298,29,12,color("#F5D58C"),true);
            txt("◈ "+shards,353,29,10,color("#9DEBFF"),true);
            txt("THE SHATTERED REALM",18,57,20,Color.WHITE,true);
            txt("SEASON 01  /  THE VEIL",18,75,9,color("#8985B5"),true);
            rect(18,88,372,145,color("#17172E"),13);
            gradient(18,88,23,145,color("#E8C879"),color("#8B57ED"),2);
            heroPortrait(48,116,0.55f);
            txt("VOIDWALKER",72,108,11,color("#F2EEFF"),true);
            txt("LEVEL "+level+"  •  "+(level<5?"WAYFARER":level<12?"RIFT HUNTER":"ABYSS SLAYER"),72,123,8,color("#A8A3CD"),true);
            bar(72,130,202,5,xp/(float)xpNeed(),color("#33304D"),color("#B18AFF"));
            txt(xp+"/"+xpNeed()+" XP",280,137,8,color("#CBB5FF"),true);
            txt("HP "+hp+"/"+actualMaxHp(),72,140,8,color("#F3A9C4"),true);
        }
        void heroPortrait(float x,float y,float scale){
            c.save();c.translate(x,y);c.scale(scale,scale);
            glow(0,0,23,color("#3A2868"),14);
            paint(color("#25223F"));c.drawCircle(0,0,22,p);
            paint(color("#C8A5FF"));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);c.drawCircle(0,0,21,p);p.setStyle(Paint.Style.FILL);
            drawHero(0,4,0.48f,false);
            c.restore();
        }
        void drawHero(float x,float y,float s,boolean attackPose){
            c.save();c.translate(x,y);c.scale(s,s);
            glow(0,36,38,color("#6440BC"),18);
            // coat tails and legs
            paint(color("#16152A"));Path coat=new Path();coat.moveTo(-19,-6);coat.lineTo(-25,28);coat.lineTo(-13,22);coat.lineTo(0,32);coat.lineTo(12,22);coat.lineTo(25,29);coat.lineTo(18,-7);coat.close();c.drawPath(coat,p);
            paint(color("#292344"));c.drawRoundRect(-17,-12,17,19,7,7,p);
            paint(color("#0D1020"));c.drawRoundRect(-15,16,-4,38,4,4,p);c.drawRoundRect(5,16,16,38,4,4,p);
            paint(color("#9B7AFF"));c.drawRoundRect(-17,34,-2,40,2,2,p);c.drawRoundRect(3,34,18,40,2,2,p);
            // shoulder armor
            paint(color("#4D3B79"));Path sh=new Path();sh.moveTo(-17,-10);sh.lineTo(-29,-7);sh.lineTo(-24,4);sh.lineTo(-14,1);sh.close();c.drawPath(sh,p);
            paint(color("#7555B5"));Path sh2=new Path();sh2.moveTo(17,-10);sh2.lineTo(28,-5);sh2.lineTo(23,5);sh2.lineTo(14,1);sh2.close();c.drawPath(sh2,p);
            // scarf and chest rune
            paint(color("#7C315C"));Path scarf=new Path();scarf.moveTo(-9,-15);scarf.lineTo(10,-14);scarf.lineTo(20,5);scarf.lineTo(6,1);scarf.lineTo(-7,-3);scarf.close();c.drawPath(scarf,p);
            paint(color("#D4B6FF"));Path rune=new Path();rune.moveTo(0,-8);rune.lineTo(5,-1);rune.lineTo(0,7);rune.lineTo(-5,-1);rune.close();c.drawPath(rune,p);
            // hood and face
            paint(color("#090D1B"));Path hood=new Path();hood.moveTo(-19,-24);hood.lineTo(-15,-42);hood.lineTo(0,-51);hood.lineTo(15,-41);hood.lineTo(19,-22);hood.lineTo(11,-13);hood.lineTo(-11,-13);hood.close();c.drawPath(hood,p);
            paint(color("#BCA4E8"));Path face=new Path();face.moveTo(-10,-31);face.lineTo(-7,-40);face.lineTo(0,-43);face.lineTo(8,-38);face.lineTo(10,-28);face.lineTo(0,-21);face.close();c.drawPath(face,p);
            paint(color("#171329"));c.drawOval(-10,-33,10,-21,p);
            glow(-5,-29,3,color("#74E9FF"),4);glow(5,-29,3,color("#74E9FF"),4);
            // blade
            c.save();c.rotate(attackPose?-45:-23,17,-4);
            paint(color("#8A5AFF"));Path blade=new Path();blade.moveTo(19,-5);blade.lineTo(25,-21);blade.lineTo(30,-48);blade.lineTo(34,-24);blade.lineTo(25,0);blade.close();c.drawPath(blade,p);
            line(20,-3,29,-31,color("#D7C8FF"),2);line(15,-5,26,3,color("#E7C9A0"),3);c.restore();
            c.restore();
        }
        void battle(){
            txt("ACTIVE EXPEDITION",18,168,9,color("#8F8AB7"),true);
            rect(18,179,372,429,color("#101225"),16);
            p.setShader(new LinearGradient(18,180,370,429,color(boss?"#3C1C43":"#29234C"),color("#111322"),Shader.TileMode.CLAMP));c.drawRoundRect(18,179,372,429,16,16,p);p.setShader(null);
            // moon, ruins, mist layers
            glow(310,224,29,color(boss?"#D14B7C":"#A3A0D5"),12);
            paint(color("#C4C3DF"));c.drawCircle(310,224,23,p);paint(color("#AAA9C8"));c.drawCircle(303,218,4,p);c.drawCircle(319,231,6,p);
            paint(color("#17172D"));Path ruins=new Path();ruins.moveTo(18,354);ruins.lineTo(48,307);ruins.lineTo(61,331);ruins.lineTo(79,281);ruins.lineTo(100,354);ruins.lineTo(126,325);ruins.lineTo(147,354);ruins.lineTo(177,300);ruins.lineTo(204,355);ruins.lineTo(233,329);ruins.lineTo(257,355);ruins.lineTo(289,302);ruins.lineTo(315,355);ruins.lineTo(344,319);ruins.lineTo(372,346);ruins.lineTo(372,429);ruins.lineTo(18,429);ruins.close();c.drawPath(ruins,p);
            paint(color("#22203F"));Path ground=new Path();ground.moveTo(18,373);ground.quadTo(190,346,372,378);ground.lineTo(372,429);ground.lineTo(18,429);ground.close();c.drawPath(ground,p);
            glow(190,388,80,color(boss?"#B12F67":"#7547CF"),25);
            paint(color("#3B2B70"));c.drawOval(72,380,315,405,p);
            // enemy detailed, each archetype different silhouette
            if(boss) drawBoss(245,345+(float)Math.sin(System.currentTimeMillis()/260.0)*3.0f,1.15f);
            else drawEnemy(245,350+(float)Math.sin(System.currentTimeMillis()/230.0)*2.5f,enemyType%5,1.0f);
            drawHero(125,358,0.83f,hitTicks>0);
            // enemy nameplate
            rect(29,191,209,266,color("#10101F"),10);
            txt(boss?"☠  RAID BOSS":"✦  "+enemyName,39,208,boss?9:10,boss?color("#FF8DAE"):Color.WHITE,true);
            txt(boss?"ANCIENT • PHASE "+(enemyHp<enemyMax/2?2:1):"ELITE • THREAT "+(1+region),39,223,8,color("#D9A3FF"),true);
            bar(39,231,155,7,enemyHp/(float)Math.max(1,enemyMax),color("#44233B"),boss?color("#FF547E"):color("#E56B9D"));
            txt(enemyHp+" / "+enemyMax+" HP",39,252,8,color("#E8B8D0"),true);
            if(boss){rect(230,190,359,222,color("#391B35"),9);center("BOSS",294,205,10,color("#FF9CB8"),true);center("Guaranteed relic drop",294,217,7,color("#E9B5CB"),false);}
            // combat info panel
            rect(18,440,372,495,color("#17172D"),12);
            txt("VITALS",30,457,8,color("#9995BE"),true);
            txt("HP",30,475,9,color("#F5B3C8"),true);bar(55,466,174,9,hp/(float)Math.max(1,actualMaxHp()),color("#442338"),color("#F05D8C"));
            txt(""+hp,235,475,9,Color.WHITE,true);
            txt("⚔ "+actualAttack(),278,475,11,color("#F2D58D"),true);
            txt("CRIT "+crit+"%",30,488,8,color("#B9A4FF"),true);
            txt("COMBO x"+Math.max(1,combo),280,488,8,color("#A7EDFF"),true);
            // action controls
            gradient(18,507,372,568,color("#A46BFF"),color("#5633B3"),13);
            center("⚔  STRIKE",195,531,16,Color.WHITE,true);
            center("Tap to attack  •  combo builds with each hit",195,551,9,color("#E8D9FF"),false);
            rect(18,580,130,633,color("#24213E"),10);txt("✦ SKILL",29,598,10,color("#D4B8FF"),true);txt("RIFT BURST",29,613,8,color("#9B94C2"),false);txt("MP "+mana+"/"+maxMana,29,625,7,color("#9B94C2"),false);
            rect(141,580,251,633,color("#24213E"),10);txt("✚ POTION",152,598,10,color("#FFB4CE"),true);txt("Heal 45%",152,613,8,color("#9B94C2"),false);txt("Have: "+potions,152,625,7,color("#9B94C2"),false);
            rect(262,580,372,633,color("#24213E"),10);txt("◈ EVENT",273,598,10,color("#A6EDFF"),true);txt("Rift invasion",273,613,8,color("#9B94C2"),false);txt(eventProgress+"/10 kills",273,625,7,color("#9B94C2"),false);
            rect(18,646,372,715,color("#111324"),10);
            txt("HUNT CONTRACT",30,663,8,color("#A79BCE"),true);
            txt("Defeat "+(questClaimed+5)+" enemies",30,681,11,Color.WHITE,true);
            bar(30,690,204,5,Math.min(1,(kills-questClaimed)/5f),color("#34314E"),color("#B18AFF"));
            rect(263,659,358,700,kills-questClaimed>=5?color("#59418A"):color("#25243B"),8);
            center(kills-questClaimed>=5?"CLAIM":"IN PROGRESS",310,676,8,kills-questClaimed>=5?Color.WHITE:color("#8583A7"),true);
            center(kills-questClaimed>=5?"+90 G  +1 SHARD":"REWARD",310,689,7,color("#E9D49B"),true);
            txt("Boss every 5 victories  •  loot is saved automatically",30,707,7,color("#727392"),false);
        }
        void drawEnemy(float x,float y,int type,float scale){
            c.save();c.translate(x,y);c.scale(scale,scale);
            int main=type==1?color("#6A332F"):type==2?color("#37637D"):type==3?color("#4A285E"):type==4?color("#35334B"):color("#25213F");
            glow(0,14,40,type==2?color("#43C9FF"):color("#B24EFF"),16);
            // feet/tendrils
            paint(color("#111222"));Path legs=new Path();
            if(type==2){legs.moveTo(-20,7);legs.lineTo(-32,26);legs.lineTo(-14,22);legs.lineTo(-5,8);legs.lineTo(8,8);legs.lineTo(17,25);legs.lineTo(30,28);legs.lineTo(18,3);}
            else {legs.moveTo(-23,0);legs.lineTo(-35,24);legs.lineTo(-20,19);legs.lineTo(-8,9);legs.lineTo(8,9);legs.lineTo(20,21);legs.lineTo(32,25);legs.lineTo(20,-1);}
            legs.close();c.drawPath(legs,p);
            paint(main);Path body=new Path();
            if(type==1){body.moveTo(-23,0);body.lineTo(-30,-25);body.lineTo(-12,-19);body.lineTo(-5,-39);body.lineTo(7,-23);body.lineTo(26,-29);body.lineTo(23,-6);body.lineTo(13,12);body.lineTo(-15,12);}
            else if(type==2){body.moveTo(-28,-2);body.lineTo(-34,-27);body.lineTo(-16,-17);body.lineTo(-5,-34);body.lineTo(9,-19);body.lineTo(30,-26);body.lineTo(24,1);body.lineTo(12,14);body.lineTo(-18,12);}
            else {body.moveTo(-24,3);body.lineTo(-22,-22);body.lineTo(-14,-39);body.lineTo(-3,-25);body.lineTo(5,-43);body.lineTo(16,-25);body.lineTo(28,-22);body.lineTo(24,0);body.lineTo(12,15);body.lineTo(-16,14);}
            body.close();c.drawPath(body,p);
            paint(type==2?color("#72B9D9"):type==1?color("#9A5148"):color("#4D356F"));c.drawOval(-18,-23,18,10,p);
            // horned skull / face
            paint(color("#171426"));Path head=new Path();head.moveTo(-18,-23);head.lineTo(-21,-42);head.lineTo(-10,-36);head.lineTo(0,-47);head.lineTo(10,-36);head.lineTo(21,-42);head.lineTo(17,-20);head.lineTo(0,-12);head.close();c.drawPath(head,p);
            glow(-8,-29,3,type==2?color("#A6F1FF"):color("#FF5FBA"),5);glow(8,-29,3,type==2?color("#A6F1FF"):color("#FF5FBA"),5);
            paint(color("#F9D9FF"));c.drawOval(-9,-30,-6,-27,p);c.drawOval(6,-30,9,-27,p);
            if(type==4){line(-24,-14,-36,-35,color("#9D88D5"),3);line(24,-14,36,-35,color("#9D88D5"),3);}
            c.restore();
        }
        void drawBoss(float x,float y,float scale){
            c.save();c.translate(x,y);c.scale(scale,scale);
            glow(0,0,65,color("#B52672"),30);
            // crown spikes
            paint(color("#211329"));Path crown=new Path();crown.moveTo(-38,-28);crown.lineTo(-48,-66);crown.lineTo(-22,-49);crown.lineTo(-8,-80);crown.lineTo(4,-48);crown.lineTo(28,-73);crown.lineTo(29,-43);crown.lineTo(45,-54);crown.lineTo(37,-21);crown.close();c.drawPath(crown,p);
            // massive cloak
            paint(color("#171120"));Path cloak=new Path();cloak.moveTo(-35,-26);cloak.lineTo(-51,10);cloak.lineTo(-58,49);cloak.lineTo(-29,36);cloak.lineTo(0,56);cloak.lineTo(26,39);cloak.lineTo(54,49);cloak.lineTo(43,4);cloak.lineTo(34,-27);cloak.close();c.drawPath(cloak,p);
            paint(color("#4C1E49"));Path armor=new Path();armor.moveTo(-28,-28);armor.lineTo(-19,-44);armor.lineTo(0,-50);armor.lineTo(20,-43);armor.lineTo(30,-23);armor.lineTo(21,15);armor.lineTo(0,31);armor.lineTo(-22,12);armor.close();c.drawPath(armor,p);
            // runic ribs
            for(int i=0;i<4;i++){line(-18+i*10,-16,-12+i*8,13,color("#C14B9A"),2);}
            // skull mask
            paint(color("#0A0B17"));Path skull=new Path();skull.moveTo(-22,-37);skull.lineTo(-17,-57);skull.lineTo(0,-65);skull.lineTo(18,-56);skull.lineTo(23,-35);skull.lineTo(12,-18);skull.lineTo(0,-13);skull.lineTo(-13,-20);skull.close();c.drawPath(skull,p);
            glow(-9,-42,5,color("#FF477E"),10);glow(9,-42,5,color("#FF477E"),10);
            paint(color("#FFE3F0"));c.drawOval(-12,-44,-6,-39,p);c.drawOval(6,-44,12,-39,p);
            // crown highlights, weapon
            line(-38,-28,-47,-56,color("#B78AFF"),2);line(29,-30,28,-62,color("#B78AFF"),2);
            line(32,-1,48,24,color("#8A63B9"),5);line(48,24,59,43,color("#F5B2FF"),3);
            c.restore();
        }
        void hero(){
            txt("HUNTER PROFILE",18,168,15,Color.WHITE,true);
            rect(18,181,372,323,color("#17172F"),14);
            gradient(28,192,111,282,color("#51408A"),color("#17142A"),12);heroPortrait(69,236,1.65f);
            txt("VOIDWALKER",124,210,13,Color.WHITE,true);txt("LEVEL "+level+"  /  "+(level<5?"WAYFARER":level<12?"RIFT HUNTER":"ABYSS SLAYER"),124,228,8,color("#B4A6D9"),true);
            txt("XP "+xp+" / "+xpNeed(),124,246,9,color("#D2B7FF"),true);bar(124,254,220,6,xp/(float)xpNeed(),color("#34304C"),color("#B58BFF"));
            txt("Skill points: "+skillPoints,124,276,9,color("#F0D38A"),true);
            stat(18,338,"ATTACK POWER",actualAttack(),color("#F1D18B"));stat(198,338,"DEFENSE",actualDefense(),color("#9EE7FF"));
            stat(18,405,"MAX HEALTH",actualMaxHp(),color("#FF9DBB"));stat(198,405,"CRITICAL",crit+"%",color("#C8A5FF"));
            stat(18,472,"BOSS SLAYERS",bossKills,color("#FF9FB8"));stat(198,472,"RELIC SHARDS",shards,color("#9DEBFF"));
            txt("EQUIPMENT FORGE",18,548,12,Color.WHITE,true);
            itemCard(18,560,180,629,"⚔","RIFTBLADE","Tier "+weaponTier+"  •  +3 ATK/tier",upgradeCost(),0);
            itemCard(190,560,372,629,"⬟","WARDEN ARMOR","Tier "+armorTier+"  •  +15 HP/tier",100+armorTier*85,1);
            itemCard(18,640,180,709,"✦","VOID RELIC","Tier "+relicTier+"  •  +ATK/DEF",140+relicTier*120,2);
            itemCard(190,640,372,709,"🧪","POTION KIT","Have: "+potions+"  •  heal 45%",45,3);
        }
        void stat(int x,int y,String name,Object val,int co){rect(x,y,x+174,y+54,color("#17172F"),10);txt(name,x+12,y+18,8,color("#8F8BAF"),true);txt(String.valueOf(val),x+12,y+42,18,co,true);}
        void itemCard(int l,int t,int r,int b,String icon,String name,String desc,int cost,int type){
            rect(l,t,r,b,color("#1B1A35"),10);txt(icon,l+9,t+21,14,type==3?color("#FF9EBB"):color("#C6A6FF"),true);txt(name,l+30,t+19,8,Color.WHITE,true);txt(desc,l+9,t+35,7,color("#A5A1C4"),false);
            rect(r-58,t+43,r-7,b-5,color("#393057"),6);center(type==3?"BUY":cost+" G",r-32,t+57,7,color("#F0D48C"),true);
        }
        void skills(){
            txt("ASCENSION TREE",18,168,15,Color.WHITE,true);txt("Spend skill points earned by leveling.",18,186,9,color("#9792BB"),false);
            rect(18,199,372,266,color("#17172F"),12);center("VOIDWALKER CORE",195,220,10,color("#C7A5FF"),true);
            glow(195,238,17,color("#6E4CC5"),10);paint(color("#B99AFF"));Path diamond=new Path();diamond.moveTo(195,221);diamond.lineTo(209,238);diamond.lineTo(195,255);diamond.lineTo(181,238);diamond.close();c.drawPath(diamond,p);
            center("SKILL POINTS: "+skillPoints,195,260,8,color("#F1D58C"),true);
            skillCard(18,280,372,367,"⚔","RIFT EDGE","Deal +35% damage on skill strike",strength,0);
            skillCard(18,378,372,465,"✚","LIFE THREAD","+18 max health per rank",vitality,1);
            skillCard(18,476,372,563,"◉","VOID FOCUS","+2% critical chance per rank",focus,2);
            skillCard(18,574,372,661,"✧","ECHO STRIKE","Every 4th hit deals bonus damage",crit-5,3);
            rect(18,674,372,715,color("#25203E"),10);center("SKILL TREE • EACH RANK COSTS 1 POINT",195,699,8,color("#C4A5FF"),true);
        }
        void skillCard(int l,int t,int r,int b,String ico,String name,String desc,int rank,int type){
            rect(l,t,r,b,color("#19182F"),11);gradient(l+8,t+10,l+49,t+51,color("#58418E"),color("#241D42"),9);center(ico,l+28,t+36,17,color("#DCC8FF"),true);
            txt(name,l+59,t+23,10,Color.WHITE,true);txt(desc,l+59,t+39,8,color("#A6A0C6"),false);txt("RANK "+rank,l+59,t+59,8,color("#C3B0F4"),true);
            rect(r-72,t+20,r-10,t+61,skillPoints>0?color("#6747A5"):color("#2D2A43"),8);center(skillPoints>0?"UPGRADE":"LOCKED",r-41,t+45,7,Color.WHITE,true);
        }
        void quests(){
            txt("CONTRACTS & EVENTS",18,168,15,Color.WHITE,true);txt("Hunt, grow stronger, claim your rewards.",18,186,9,color("#9792BB"),false);
            rect(18,199,372,303,color("#211A36"),12);gradient(18,199,372,205,color("#F0D18A"),color("#8C58DF"),2);
            txt("✦  RIFT INVASION",31,223,12,color("#BDEFFF"),true);txt("Limited-time encounter • no internet needed",31,241,8,color("#B4ACD0"),false);
            txt("Seal rifts by defeating 10 enemies.",31,260,9,Color.WHITE,true);bar(31,271,230,7,eventProgress/10f,color("#34304D"),color("#8DEBFF"));
            txt(eventProgress+"/10",270,279,9,color("#9DEBFF"),true);
            rect(281,216,357,253,eventProgress>=10&&eventClaimed==0?color("#4D4B84"):color("#2D2942"),8);center(eventClaimed>0?"CLAIMED":eventProgress>=10?"CLAIM":"IN PROGRESS",319,234,7,Color.WHITE,true);center("+180 G",319,246,8,color("#F1D58D"),true);
            questCard(18,316,372,407,"01","THE HUNTER'S PATH","Defeat 5 enemies",Math.min(5,kills),5,"+90 gold");
            questCard(18,420,372,511,"02","BOSS BREAKER","Defeat 1 raid boss",Math.min(1,bossKills),1,"+2 shards");
            questCard(18,524,372,615,"03","RELIC SEEKER","Collect 3 relic shards",Math.min(3,shards),3,"+1 potion");
            rect(18,628,372,715,color("#17172F"),11);txt("DAILY BLESSING",31,650,10,color("#F1D58D"),true);txt("Earn a reward after your next hunt.",31,670,8,color("#A5A1C4"),false);center(dailyClaimed>0?"TODAY'S GIFT CLAIMED":kills>0?"TAP TO CLAIM DAILY GIFT":"KILL 1 ENEMY TO CHARGE",195,699,8,color("#BCA3FF"),true);
        }
        void questCard(int l,int t,int r,int b,String num,String name,String desc,int progress,int goal,String reward){
            rect(l,t,r,b,color("#19182F"),10);txt(num,l+11,t+20,8,color("#A990E8"),true);txt(name,l+38,t+20,9,Color.WHITE,true);txt(desc,l+38,t+37,8,color("#A5A1C4"),false);
            bar(l+38,t+47,150,5,progress/(float)goal,color("#34304D"),color("#B18AFF"));txt(progress+"/"+goal,l+194,t+53,7,color("#C7B4F3"),true);txt(reward,l+38,t+69,8,color("#F1D58D"),true);
            rect(r-78,t+24,r-9,t+61,progress>=goal?color("#51407E"):color("#2B2940"),7);center(progress>=goal?"READY":"HUNT",r-43,t+46,7,Color.WHITE,true);
        }
        void world(){
            txt("EXPEDITION MAP",18,168,15,Color.WHITE,true);txt("Defeat the guardian to unlock the next realm.",18,186,9,color("#9792BB"),false);
            realmCard(18,199,372,299,0,"THE VEIL","Corrupted shades • recommended Lv. 1",color("#58458D"),true);
            realmCard(18,311,372,411,1,"ASHEN HOLLOW","Cinder beasts • recommended Lv. 5",color("#8E4D40"),level>=5);
            realmCard(18,423,372,523,2,"FROSTBOUND","Frost giants • recommended Lv. 12",color("#376D91"),level>=12);
            realmCard(18,535,372,635,3,"THE ABYSS","Ancient horrors • recommended Lv. 20",color("#87315E"),level>=20);
            rect(18,648,372,715,color("#17172F"),11);txt("RAID BOARD",31,668,10,color("#FF9FB8"),true);txt("The Hollow King emerges every 5 victories.",31,686,9,color("#D1C5E9"),false);txt("Boss defeats: "+bossKills+"  •  Relic shards: "+shards,31,702,8,color("#BBA4F4"),true);
        }
        void realmCard(int l,int t,int r,int b,int idx,String name,String desc,int co,boolean unlocked){
            gradient(l,t,r,b,co,color("#17172D"),12);glow(r-43,t+45,25,co,12);
            // mini illustrated landscape
            paint(Color.argb(100,0,0,0));Path mountain=new Path();mountain.moveTo(r-92,t+75);mountain.lineTo(r-72,t+30);mountain.lineTo(r-55,t+55);mountain.lineTo(r-35,t+20);mountain.lineTo(r-7,t+75);mountain.close();c.drawPath(mountain,p);
            txt("0"+(idx+1),l+13,t+21,8,color("#E0CBFF"),true);txt(name,l+13,t+42,12,Color.WHITE,true);txt(desc,l+13,t+59,7,color("#D2C9E8"),false);
            rect(l+13,t+69,l+114,t+91,unlocked?color("#493B73"):color("#27243A"),6);center(unlocked?"ENTER REALM":"LOCKED",l+63,t+83,7,unlocked?Color.WHITE:color("#77738F"),true);
        }
        void nav(){
            rect(0,735,390,844,color("#0A0B17"),0);rect(18,744,372,745,color("#282640"),1);
            String[] labels={"BATTLE","HERO","SKILLS","QUESTS","WORLD"};String[] icons={"⚔","♙","✧","☷","⌖"};
            for(int i=0;i<5;i++){float x=39+i*78;int co=tab==i?color("#D0ACFF"):color("#777691");if(tab==i)rect(x-25,750,x+25,806,color("#29223F"),10);center(icons[i],x,772,18,co,true);center(labels[i],x,792,7,co,true);}
            center("PROJECT: ASCENSION  •  OFFLINE DARK RPG",195,825,7,color("#555471"),true);
            txt(audioOn?"♪ ON":"♪ OFF",333,824,7,audioOn?color("#9EEBFF"):color("#77718A"),true);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            float x=e.getX()/sx,y=e.getY()/sy;
            if(y>=735){if(x>=315){audioOn=!audioOn;if(audioOn)play(sClick);}else{tab=Math.min(4,Math.max(0,(int)(x/78)));play(sClick);}invalidate();return true;}
            if(tab==0){
                if(y>=507&&y<=570)attackEnemy();
                else if(y>=580&&y<=636){if(x<137)riftBurst();else if(x<255)usePotion();else {tab=3;}}
                else if(y>=646&&y<=715&&kills-questClaimed>=5){gold+=90;shards++;questClaimed+=5;play(sWin);save();}
            }else if(tab==1){
                if(y>=560&&y<=629){if(x<184)buyUpgrade(0);else buyUpgrade(1);}
                else if(y>=640&&y<=709){if(x<184)buyUpgrade(2);else buyUpgrade(3);}
            }else if(tab==2){
                if(y>=280&&y<=367)upgradeSkill(0);
                else if(y>=378&&y<=465)upgradeSkill(1);
                else if(y>=476&&y<=563)upgradeSkill(2);
                else if(y>=574&&y<=661)upgradeSkill(3);
            }else if(tab==3){
                if(y>=199&&y<=303&&eventProgress>=10&&eventClaimed==0){gold+=180;shards+=2;eventClaimed=1;play(sWin);save();}
                else if(y>=316&&y<=407&&kills-questClaimed>=5){gold+=90;shards++;questClaimed+=5;play(sWin);save();}
                else if(y>=420&&y<=511&&bossKills>prefs.getInt("claimedBossKills",0)){shards+=2;prefs.edit().putInt("claimedBossKills",bossKills).apply();gold+=120;play(sWin);save();}
                else if(y>=524&&y<=615&&shards>=3){shards-=3;potions++;play(sBuy);save();}
                else if(y>=628&&y<=715&&kills>0&&dailyClaimed==0){dailyClaimed=1;gold+=55;potions++;play(sWin);save();}
            }else if(tab==4){
                if(y>=199&&y<=299){region=0;spawnEnemy();}
                else if(y>=311&&y<=411&&level>=5){region=1;spawnEnemy();}
                else if(y>=423&&y<=523&&level>=12){region=2;spawnEnemy();}
                else if(y>=535&&y<=635&&level>=20){region=3;spawnEnemy();}
            }
            invalidate();return true;
        }
        void attackEnemy(){
            long now=System.currentTimeMillis();if(now-lastAttackAt<160)return;lastAttackAt=now;
            combo=Math.min(99,combo+1);hitTicks=4;shakeTicks=3;mana=Math.min(maxMana,mana+4+focus);
            boolean critical=rng.nextInt(100)<Math.min(70,crit+focus*2);
            int damage=actualAttack()+rng.nextInt(Math.max(3,actualAttack()/3+1));if(critical)damage=(int)(damage*1.85);
            if(combo%4==0)damage+=actualAttack()/2+strength*6;
            enemyHp-=damage;play(critical?sCrit:sHit);flashTicks=critical?4:1;
            if(enemyHp<=0){victory();}
            else{
                int enemyHit=(boss?19+region*5:10+region*5+level/3);
                if(boss&&enemyHp<enemyMax/2)enemyHit+=12;
                hp-=Math.max(3,enemyHit-actualDefense());if(hp<=0){hp=Math.max(1,actualMaxHp()/2);gold=Math.max(0,gold-15);combo=0;flashTicks=6;}
            }
            save();invalidate();
        }
        void victory(){
            kills++;eventProgress=Math.min(10,eventProgress+1);gold+=boss?120+region*35:18+region*10;xp+=boss?115+region*35:25+region*12;
            if(rng.nextInt(100)<22||boss){shards+=boss?2:1;}
            if(rng.nextInt(100)<18){potions++;}
            if(boss){bossKills++;play(sWin);gold+=60;maxHp+=8;attack+=2;}
            if(xp>=xpNeed()){xp-=xpNeed();level++;skillPoints++;maxHp+=16;maxMana+=12;mana=maxMana;hp=actualMaxHp();attack+=2;defense++;play(sLevel);flashTicks=7;}
            hp=Math.min(actualMaxHp(),hp+Math.max(8,actualMaxHp()/12));
            spawnEnemy();save();
        }
        void spawnEnemy(){
            boss=(kills>0&&kills%5==0&&bossKills<kills/5+1);
            if(boss){enemyName="THE HOLLOW KING";enemyMax=260+level*38+region*75;enemyHp=enemyMax;play(sBoss);}
            else{enemyType=rng.nextInt(5);enemyName=enemyLabel(enemyType);enemyMax=65+level*13+region*35+enemyType*9;enemyHp=enemyMax;}
            combo=0;save();
        }
        void riftBurst(){
            if(mana<25){play(sClick);return;}
            mana-=25;
            int damage=(int)(actualAttack()*(1.75+strength*0.35));enemyHp-=damage;play(sSkill);flashTicks=5;
            hp=Math.min(actualMaxHp(),hp+vitality*4);
            if(enemyHp<=0)victory();else hp=Math.max(1,hp-Math.max(1,8-actualDefense()));
            save();
        }
        void usePotion(){if(potions>0&&hp<actualMaxHp()){potions--;hp=Math.min(actualMaxHp(),hp+Math.max(45,actualMaxHp()*45/100));play(sHeal);save();}else play(sClick);}
        void buyUpgrade(int type){
            int cost=type==0?upgradeCost():type==1?100+armorTier*85:type==2?140+relicTier*120:45;
            if(type==3){if(gold>=cost){gold-=cost;potions++;play(sBuy);save();}return;}
            if(gold<cost){play(sClick);return;}
            gold-=cost;if(type==0)weaponTier++;else if(type==1){armorTier++;maxHp+=15;hp+=15;}else{relicTier++;shards=Math.max(0,shards-1);}
            play(sBuy);save();
        }
        void upgradeSkill(int type){
            if(skillPoints<=0){play(sClick);return;}skillPoints--;
            if(type==0){strength++;attack+=1;}
            else if(type==1){vitality++;maxHp+=18;hp+=18;}
            else if(type==2){focus++;crit+=2;maxMana+=10;mana+=10;}
            else {crit+=3;}
            play(sSkill);save();
        }
    }
}