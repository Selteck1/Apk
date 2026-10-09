package com.selteck.ascension;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.*;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.media.MediaPlayer;
import android.graphics.drawable.Drawable;
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
        int weaponId, armorId, weaponPower, armorPower, weaponRarity, armorRarity, casesOpened, stageWins, weaponCollection=1, armorCollection=1;
        int[] ownedWeaponPower=new int[8], ownedWeaponRarity=new int[8], ownedArmorPower=new int[8], ownedArmorRarity=new int[8];
        int[] regionKills=new int[6], regionBosses=new int[6];
        Bitmap[] realmArt=new Bitmap[6], mobArt=new Bitmap[7], bossArt=new Bitmap[6], weaponArt=new Bitmap[8], armorArt=new Bitmap[8], caseArt=new Bitmap[3];
        Bitmap heroIdleArt, heroAttackArt, heroJumpArt;
        float heroX=112, enemyX=270, heroJump=0; long jumpUntil=0, invulnerableUntil=0, nextEnemyAttack=0, lastStep=0;
        String lootNotice="Explore the Shattered Realm", lastLoot="No loot yet"; long lootNoticeUntil=0;
        int enemyHp, enemyMax, enemyType, region, flashTicks, hitTicks, shakeTicks;
        String combatNotice=""; long combatNoticeUntil=0;
        boolean boss, eventActive, audioOn=true, showBag=false, skillBurst=false;
        String enemyName="RIFT WRAITH";
        long eventSeed;
        SoundPool sounds; MediaPlayer ambience; int sHit, sCrit, sBoss, sLevel, sBuy, sHeal, sWin, sClick, sSkill;
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
            weaponId=prefs.getInt("weaponId",0); armorId=prefs.getInt("armorId",0); weaponPower=prefs.getInt("weaponPower",0); armorPower=prefs.getInt("armorPower",0); weaponRarity=prefs.getInt("weaponRarity",0); armorRarity=prefs.getInt("armorRarity",0); casesOpened=prefs.getInt("casesOpened",0); stageWins=prefs.getInt("stageWins",0); weaponCollection=prefs.getInt("weaponCollection",1); armorCollection=prefs.getInt("armorCollection",1);
            for(int i=0;i<8;i++){ownedWeaponPower[i]=prefs.getInt("wp"+i,0);ownedWeaponRarity[i]=prefs.getInt("wr"+i,0);ownedArmorPower[i]=prefs.getInt("ap"+i,0);ownedArmorRarity[i]=prefs.getInt("ar"+i,0);}
            for(int i=0;i<6;i++){regionKills[i]=prefs.getInt("rk"+i,i==0?Math.max(0,kills-bossKills):0);regionBosses[i]=prefs.getInt("rb"+i,i==0?bossKills:0);}
            enemyType=prefs.getInt("enemyType",0); boss=prefs.getBoolean("boss",false);
            enemyMax=prefs.getInt("enemyMax",90+level*9); enemyHp=prefs.getInt("enemyHp",enemyMax);
            if(hp<1) hp=maxHp;
            if(enemyHp<1) enemyHp=enemyMax;
            enemyName=boss?"THE HOLLOW KING":enemyLabel(enemyType);
            eventSeed=prefs.getLong("eventSeed",System.currentTimeMillis()/86400000L);
            long today=System.currentTimeMillis()/86400000L; if(eventSeed!=today){eventSeed=today;eventProgress=0;eventClaimed=0;dailyClaimed=0;}
            mana=Math.max(0,Math.min(maxMana,mana));
            setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            initSounds(); initAmbient(); loadArt();
            setContentDescription("PROJECT ASCENSION dark fantasy role-playing game");
            postInvalidateDelayed(40);
        }
        Bitmap vectorBitmap(int id,int w,int h){try{Drawable d=getResources().getDrawable(id);d=d.mutate();Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas cc=new Canvas(b);d.setBounds(0,0,w,h);d.draw(cc);return b;}catch(Exception ex){return null;}}
        Bitmap crop(Bitmap b,int x,int y,int w,int h){try{return b==null?null:Bitmap.createBitmap(b,x,y,w,h);}catch(Exception ex){return null;}}
        void loadArt(){
            Bitmap ws=vectorBitmap(R.drawable.art_worlds,390,1500);for(int i=0;i<6;i++)realmArt[i]=crop(ws,0,i*250,390,250);
            Bitmap hs=vectorBitmap(R.drawable.art_heroes,96,384);heroIdleArt=crop(hs,0,0,96,128);heroAttackArt=crop(hs,0,128,96,128);heroJumpArt=crop(hs,0,256,96,128);
            Bitmap es=vectorBitmap(R.drawable.art_enemies,384,512);for(int i=0;i<7;i++)mobArt[i]=crop(es,(i%4)*96,(i/4)*128,96,128);for(int i=0;i<6;i++){int ix=7+i;bossArt[i]=crop(es,(ix%4)*96,(ix/4)*128,96,128);}
            Bitmap its=vectorBitmap(R.drawable.art_items,400,384);for(int i=0;i<8;i++){weaponArt[i]=crop(its,(i%5)*80,(i/5)*96,80,96);int j=8+i;armorArt[i]=crop(its,(j%5)*80,(j/5)*96,80,96);}for(int i=0;i<3;i++){int j=16+i;caseArt[i]=crop(its,(j%5)*80,(j/5)*96,80,96);}
        }
        void drawSprite(Bitmap b,float l,float t,float w,float h){if(b==null)return;paint(Color.WHITE);p.setFilterBitmap(true);c.drawBitmap(b,null,new RectF(l,t,l+w,t+h),p);}
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
        void initAmbient(){try{int rate=22050;double seconds=8.0;int n=(int)(rate*seconds);ByteBuffer b=ByteBuffer.allocate(44+n*2).order(ByteOrder.LITTLE_ENDIAN);b.put(new byte[]{'R','I','F','F'});b.putInt(36+n*2);b.put(new byte[]{'W','A','V','E','f','m','t',' '});b.putInt(16);b.putShort((short)1);b.putShort((short)1);b.putInt(rate);b.putInt(rate*2);b.putShort((short)2);b.putShort((short)16);b.put(new byte[]{'d','a','t','a'});b.putInt(n*2);for(int i=0;i<n;i++){double t=i/(double)rate;double env=Math.pow(Math.max(0,Math.sin(Math.PI*t/seconds)),0.45);double wave=Math.sin(2*Math.PI*55*t)*0.24+Math.sin(2*Math.PI*82.5*t)*0.20+Math.sin(2*Math.PI*110*t+Math.sin(t*0.5))*0.12+Math.sin(2*Math.PI*165*t)*0.075;wave+=Math.sin(2*Math.PI*41.2*t)*0.08*Math.sin(t*0.8);short v=(short)(Math.max(-1,Math.min(1,wave*env*0.32))*32767);b.putShort(v);}File f=new File(getCacheDir(),"asc_ambience.wav");FileOutputStream out=new FileOutputStream(f);out.write(b.array());out.close();ambience=new MediaPlayer();ambience.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());ambience.setDataSource(f.getAbsolutePath());ambience.prepare();ambience.setLooping(true);ambience.setVolume(0.22f,0.22f);if(audioOn)ambience.start();}catch(Exception ignored){if(ambience!=null){try{ambience.release();}catch(Exception ignored2){}ambience=null;}}}
        void play(int id) { if(audioOn&&sounds!=null&&id!=0) try { sounds.play(id,1,1,1,0,0.9f+rng.nextFloat()*0.22f); } catch(Exception ignored){} }
        void toggleAudio(){audioOn=!audioOn;if(ambience!=null){try{if(audioOn){if(!ambience.isPlaying())ambience.start();}else if(ambience.isPlaying())ambience.pause();}catch(Exception ignored){}}if(audioOn)play(sClick);}
        void pauseAudio(){if(sounds!=null)sounds.autoPause();if(ambience!=null)try{if(ambience.isPlaying())ambience.pause();}catch(Exception ignored){}}
        void resumeAudio(){if(sounds!=null)sounds.autoResume();if(audioOn&&ambience!=null)try{if(!ambience.isPlaying())ambience.start();}catch(Exception ignored){}}
        void releaseAudio(){if(sounds!=null){sounds.release();sounds=null;}if(ambience!=null){try{ambience.stop();}catch(Exception ignored){}ambience.release();ambience=null;}}
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
            .putInt("eventProgress",eventProgress).putInt("weaponCollection",weaponCollection).putInt("armorCollection",armorCollection).putInt("weaponId",weaponId).putInt("armorId",armorId).putInt("weaponPower",weaponPower).putInt("armorPower",armorPower).putInt("weaponRarity",weaponRarity).putInt("armorRarity",armorRarity).putInt("casesOpened",casesOpened).putInt("stageWins",stageWins).putInt("enemyType",enemyType).putBoolean("boss",boss).putInt("enemyMax",enemyMax).putInt("enemyHp",enemyHp)
            .putLong("eventSeed",eventSeed).apply();SharedPreferences.Editor e=prefs.edit();for(int i=0;i<6;i++)e.putInt("rk"+i,regionKills[i]).putInt("rb"+i,regionBosses[i]);e.apply();}
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
            int[] sky={color("#19152F"),color("#321D22"),color("#122B43"),color("#112F3A"),color("#281C47"),color("#32152F")}; int[] glowPal={color("#40316E"),color("#9E4A28"),color("#418CB5"),color("#2AABAC"),color("#8B65D5"),color("#D2387C")};
            p.setShader(new LinearGradient(0,0,340,844,sky[Math.floorMod(region,sky.length)],color("#080914"),Shader.TileMode.CLAMP));c.drawRect(0,0,390,844,p);p.setShader(null);
            glow(315,230,65,glowPal[Math.floorMod(region,glowPal.length)],55);glow(40,470,55,glowPal[Math.floorMod(region,glowPal.length)],42);
            long anim=System.currentTimeMillis();for(int i=0;i<52;i++){float xx=(i*73+17)%390, yy=(i*131+19+(float)((anim/70+i*13)%710))%710;paint(Color.argb(80+(i%5)*28,173,176,255));c.drawCircle(xx,yy,0.5f+(i%3)*0.4f,p);}
            int ambience=realmAccent();for(int i=0;i<17;i++){float xx=(i*47+17+(float)Math.sin(anim/480.0+i)*11)%390;float yy=(i*61+(float)(anim/(region==2?85:region==3?-110:105))%710)%710;if(region==3)yy=710-yy;paint(Color.argb(95+(i%4)*30,Color.red(ambience),Color.green(ambience),Color.blue(ambience)));c.drawCircle(xx,yy,1+(i%3)*0.6f,p);}
            header();
            tickCombat(); if(tab==0)battle();else if(tab==1)hero();else if(tab==2)skills();else if(tab==3)cases();else if(tab==4)quests();else world();
            nav(); if(flashTicks>0){rect(0,0,390,735,Color.argb(Math.min(90,flashTicks*12),210,85,140),0);flashTicks--;}
            if(hitTicks>0)hitTicks--; c.restore();postInvalidateDelayed(45);
        }
        void header(){
            txt("P R O J E C T   /   A S C E N S I O N",18,28,9,color("#AAA1D2"),true);
            txt("✦ "+gold,298,29,12,color("#F5D58C"),true);
            txt("◈ "+shards,353,29,10,color("#9DEBFF"),true);
            txt("THE SHATTERED REALM",18,57,20,Color.WHITE,true);
            txt("EXPEDITION "+(region+1)+"  /  "+regionName(),18,75,9,color("#8985B5"),true);
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
            if(heroIdleArt!=null){c.save();Path clip=new Path();clip.addCircle(0,0,20,Path.Direction.CW);c.clipPath(clip);drawSprite(heroIdleArt,-20,-27,40,54);c.restore();}
            else drawHero(0,4,0.48f,false);
            c.restore();
        }
        int realmAccent(){int[] a={color("#9E72EF"),color("#E48743"),color("#6ECDF3"),color("#42D8CB"),color("#B49AFF"),color("#F05BA6")};return a[Math.floorMod(region,a.length)];}
        void tickCombat(){if(tab!=0)return;long now=System.currentTimeMillis();if(Math.abs(enemyX-heroX)>86)enemyX+=heroX>enemyX?0.62f:-0.62f;enemyX=Math.max(55,Math.min(342,enemyX));if(now>=nextEnemyAttack&&Math.abs(enemyX-heroX)<96){nextEnemyAttack=now+(boss?1280:1650);if(now<jumpUntil||now<invulnerableUntil){combatNotice="DODGED";combatNoticeUntil=now+550;play(sClick);}else{int hurt=Math.max(5,(boss?22+region*6:12+region*5+level/4)-actualDefense()/2);hp-=hurt;invulnerableUntil=now+720;flashTicks=4;combatNotice=(boss?"BOSS SMASH":"ENEMY STRIKE")+"  -"+hurt;combatNoticeUntil=now+850;play(sHit);if(hp<=0){hp=Math.max(1,actualMaxHp()/2);gold=Math.max(0,gold-25);heroX=Math.max(60,heroX-38);combo=0;combatNotice="YOU FELL • LOST 25 GOLD";combatNoticeUntil=now+1500;invulnerableUntil=now+1900;}save();}}}
        void drawHero(float x,float y,float s,boolean attackPose){
            c.save();c.translate(x,y);c.scale(s,s);
            glow(0,36,38,color("#6440BC"),18);
            // coat tails and legs
            paint(color("#16152A"));Path coat=new Path();float flap=(float)Math.sin(System.currentTimeMillis()/105.0)*2.2f;coat.moveTo(-19,-6);coat.lineTo(-25,28+flap);coat.lineTo(-13,22-flap);coat.lineTo(0,32);coat.lineTo(12,22+flap);coat.lineTo(25,29-flap);coat.lineTo(18,-7);coat.close();c.drawPath(coat,p);
            paint(armorPower>15?rarityColor(armorRarity):color("#292344"));c.drawRoundRect(-17,-12,17,19,7,7,p);
            paint(color("#0D1020"));c.drawRoundRect(-15,16,-4,38,4,4,p);c.drawRoundRect(5,16,16,38,4,4,p);
            paint(color("#9B7AFF"));c.drawRoundRect(-17,34,-2,40,2,2,p);c.drawRoundRect(3,34,18,40,2,2,p);
            // shoulder armor
            paint(armorPower>15?rarityColor(armorRarity):color("#4D3B79"));Path sh=new Path();sh.moveTo(-17,-10);sh.lineTo(-29,-7);sh.lineTo(-24,4);sh.lineTo(-14,1);sh.close();c.drawPath(sh,p);
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
            c.save();float swing=attackPose?(-70+(10-hitTicks)*16f):-23f;c.rotate(swing,17,-4);
            paint(rarityColor(weaponRarity));Path blade=new Path();if(weaponId%3==1){blade.moveTo(17,-4);blade.lineTo(22,-27);blade.lineTo(31,-56);blade.lineTo(36,-27);blade.lineTo(26,2);blade.close();}else if(weaponId%3==2){blade.moveTo(18,-6);blade.lineTo(24,-32);blade.lineTo(28,-58);blade.lineTo(31,-32);blade.lineTo(25,0);blade.close();}else{blade.moveTo(19,-5);blade.lineTo(25,-21);blade.lineTo(30,-48);blade.lineTo(34,-24);blade.lineTo(25,0);blade.close();}c.drawPath(blade,p);
            line(20,-3,29,-31,rarityColor(weaponRarity),2);line(15,-5,26,3,color("#E7C9A0"),3);c.restore();
            c.restore();
        }
        void battle(){
            txt("ACTIVE EXPEDITION",18,168,9,color("#8F8AB7"),true);
            rect(18,179,372,429,color("#101225"),16);
            c.save();Path arenaClip=new Path();arenaClip.addRoundRect(18,179,372,429,16,16,Path.Direction.CW);c.clipPath(arenaClip);
            if(realmArt[Math.floorMod(region,realmArt.length)]!=null){drawSprite(realmArt[Math.floorMod(region,realmArt.length)],18,179,354,250);paint(Color.argb(38,7,7,22));c.drawRect(18,179,372,429,p);}
            else{p.setShader(new LinearGradient(18,180,370,429,Color.rgb((Color.red(realmAccent())+Color.red(color("#161527")))/2,(Color.green(realmAccent())+Color.green(color("#161527")))/2,(Color.blue(realmAccent())+Color.blue(color("#161527")))/2),color("#111322"),Shader.TileMode.CLAMP));c.drawRoundRect(18,179,372,429,16,16,p);p.setShader(null);}
            paint(Color.argb(185,5,7,16));Path ground=new Path();ground.moveTo(18,374);ground.quadTo(190,347,372,378);ground.lineTo(372,429);ground.lineTo(18,429);ground.close();c.drawPath(ground,p);
            glow(190,390,82,realmAccent(),22);paint(Color.rgb(Color.red(realmAccent())/3,Color.green(realmAccent())/3,Color.blue(realmAccent())/3));c.drawOval(65,380,322,408,p);
            c.restore();
            // enemy detailed, each archetype different silhouette
            long nowArt=System.currentTimeMillis();float enemyBob=(float)Math.sin(nowArt/(boss?260.0:230.0))*3.0f;
            if(boss){if(bossArt[Math.floorMod(region,bossArt.length)]!=null)drawSprite(bossArt[Math.floorMod(region,bossArt.length)],enemyX-48,281+enemyBob,96,132);else drawBoss(enemyX,345+enemyBob,1.15f);}
            else{if(mobArt[Math.floorMod(enemyType,mobArt.length)]!=null)drawSprite(mobArt[Math.floorMod(enemyType,mobArt.length)],enemyX-40,294+enemyBob,80,116);else drawEnemy(enemyX,350+enemyBob,enemyType%7,1.0f);}
            Bitmap heroFrame=nowArt<jumpUntil?heroJumpArt:(hitTicks>0?heroAttackArt:heroIdleArt);
            if(heroFrame!=null){float heroTop=nowArt<jumpUntil?273:306;drawSprite(heroFrame,heroX-33,heroTop,66,104);line(heroX+13,353,heroX+27,326,rarityColor(weaponRarity),2.5f);}
            else drawHero(heroX,358-(nowArt<jumpUntil?42:0),0.83f,hitTicks>0);
            if(hitTicks>0){ paint(Color.argb(235,220,242,255));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);c.drawArc(heroX+8,317,heroX+93,396,-72+(10-hitTicks)*17,105,false,p);p.setStyle(Paint.Style.FILL); for(int k=0;k<7;k++){float a=(System.currentTimeMillis()/19+k*51)%360;float rr=16+(k*7);paint(Color.argb(150,198,145,255));c.drawCircle(heroX+43+(float)Math.cos(Math.toRadians(a))*rr,355+(float)Math.sin(Math.toRadians(a))*rr,1.5f+(k%3),p);} }
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
            rect(18,507,88,568,color("#292441"),11);center("◀",53,539,19,color("#CBB2FF"),true);center("STEP",53,554,7,color("#A5A0C5"),true);
            rect(94,507,157,568,color("#292441"),11);center("JUMP",125,535,10,color("#9DEBFF"),true);center("DODGE",125,551,7,color("#A5A0C5"),true);
            gradient(163,507,275,568,color("#A46BFF"),color("#5633B3"),11);center("⚔ ATTACK",219,536,12,Color.WHITE,true);center("combo strike",219,552,7,color("#E8D9FF"),false);
            rect(281,507,372,568,color("#292441"),11);center("▶",326,539,19,color("#CBB2FF"),true);center("STEP",326,554,7,color("#A5A0C5"),true);
            rect(18,580,130,633,color("#24213E"),10);txt("✦ SKILL",29,598,10,color("#D4B8FF"),true);txt("RIFT BURST",29,613,8,color("#9B94C2"),false);txt("MP "+mana+"/"+maxMana,29,625,7,color("#9B94C2"),false);
            rect(141,580,251,633,color("#24213E"),10);txt("✚ POTION",152,598,10,color("#FFB4CE"),true);txt("Heal 45%",152,613,8,color("#9B94C2"),false);txt("Have: "+potions,152,625,7,color("#9B94C2"),false);
            rect(262,580,372,633,color("#24213E"),10);txt("◈ CASES",273,598,10,color("#A6EDFF"),true);txt("Open loot",273,613,8,color("#9B94C2"),false);txt(casesOpened+" opened",273,625,7,color("#9B94C2"),false);
            rect(18,646,372,715,color("#111324"),10);
            if(System.currentTimeMillis()<combatNoticeUntil)center(combatNotice,195,420,10,color("#F7D7FF"),true);
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
            glow(0,14,40,type==2?color("#43C9FF"):realmAccent(),16);
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
            if(type==5){paint(color("#A996D3"));Path veil=new Path();veil.moveTo(-13,-14);veil.lineTo(-26,7);veil.lineTo(-14,3);veil.lineTo(-6,20);veil.lineTo(2,3);veil.lineTo(18,14);veil.lineTo(13,-14);veil.close();c.drawPath(veil,p);}
            if(type==6){paint(color("#7B5A9A"));Path snout=new Path();snout.moveTo(7,-30);snout.lineTo(28,-23);snout.lineTo(13,-17);snout.close();c.drawPath(snout,p);line(-13,-34,-20,-52,color("#C5B0FF"),3);line(4,-35,11,-54,color("#C5B0FF"),3);}
            glow(-8,-29,3,type==2?color("#A6F1FF"):color("#FF5FBA"),5);glow(8,-29,3,type==2?color("#A6F1FF"):color("#FF5FBA"),5);
            paint(color("#F9D9FF"));c.drawOval(-9,-30,-6,-27,p);c.drawOval(6,-30,9,-27,p);
            if(type==4){line(-24,-14,-36,-35,color("#9D88D5"),3);line(24,-14,36,-35,color("#9D88D5"),3);}
            c.restore();
        }
        void drawBoss(float x,float y,float scale){
            c.save();c.translate(x,y);c.scale(scale,scale);
            glow(0,0,65,realmAccent(),30);
            // crown spikes
            paint(color("#211329"));Path crown=new Path();crown.moveTo(-38,-28);crown.lineTo(-48,-66);crown.lineTo(-22,-49);crown.lineTo(-8,-80);crown.lineTo(4,-48);crown.lineTo(28,-73);crown.lineTo(29,-43);crown.lineTo(45,-54);crown.lineTo(37,-21);crown.close();c.drawPath(crown,p);
            // each guardian has a distinct silhouette beyond its palette
            if(region==4){paint(color("#3B2862"));Path wings=new Path();wings.moveTo(-24,-18);wings.lineTo(-68,-52);wings.lineTo(-60,-12);wings.lineTo(-49,8);wings.lineTo(-22,3);wings.lineTo(23,-18);wings.lineTo(66,-52);wings.lineTo(58,-10);wings.lineTo(45,9);wings.lineTo(22,3);wings.close();c.drawPath(wings,p);line(-62,-44,-48,2,realmAccent(),2);line(61,-43,47,2,realmAccent(),2);}
            if(region==3){paint(color("#17404D"));for(int i=0;i<5;i++){Path tent=new Path();tent.moveTo(-26+i*13,10);tent.quadTo(-45+i*21,34+(i%2)*9,-38+i*20,48);c.drawPath(tent,p);}}
            if(region==2){paint(color("#A1DDF1"));Path ice=new Path();ice.moveTo(-30,-27);ice.lineTo(-39,-62);ice.lineTo(-18,-42);ice.lineTo(-8,-74);ice.lineTo(3,-43);ice.lineTo(20,-63);ice.lineTo(30,-27);ice.close();c.drawPath(ice,p);}
            if(region==5){paint(Color.argb(130,Color.red(realmAccent()),Color.green(realmAccent()),Color.blue(realmAccent())));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);c.drawOval(-54,-80,54,34,p);c.drawOval(-64,-90,64,44,p);p.setStyle(Paint.Style.FILL);}
            // massive cloak
            paint(color("#171120"));Path cloak=new Path();float flap=(float)Math.sin(System.currentTimeMillis()/125.0)*4f;cloak.moveTo(-35,-26);cloak.lineTo(-51,10);cloak.lineTo(-58,49+flap);cloak.lineTo(-29,36+flap*0.4f);cloak.lineTo(0,56);cloak.lineTo(26,39-flap*0.3f);cloak.lineTo(54,49-flap);cloak.lineTo(43,4);cloak.lineTo(34,-27);cloak.close();c.drawPath(cloak,p);
            paint(realmAccent());Path armor=new Path();armor.moveTo(-28,-28);armor.lineTo(-19,-44);armor.lineTo(0,-50);armor.lineTo(20,-43);armor.lineTo(30,-23);armor.lineTo(21,15);armor.lineTo(0,31);armor.lineTo(-22,12);armor.close();c.drawPath(armor,p);
            // runic ribs
            for(int i=0;i<4;i++){line(-18+i*10,-16,-12+i*8,13,realmAccent(),2);}
            // skull mask
            paint(color("#0A0B17"));Path skull=new Path();skull.moveTo(-22,-37);skull.lineTo(-17,-57);skull.lineTo(0,-65);skull.lineTo(18,-56);skull.lineTo(23,-35);skull.lineTo(12,-18);skull.lineTo(0,-13);skull.lineTo(-13,-20);skull.close();c.drawPath(skull,p);
            glow(-9,-42,5,realmAccent(),10);glow(9,-42,5,realmAccent(),10);
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
            txt("WPN  "+weaponNames()[weaponId]+"  •  "+rarityName(weaponRarity),124,293,7.5f,rarityColor(weaponRarity),true);
            txt("ARM  "+armorNames()[armorId]+"  •  "+rarityName(armorRarity),124,307,7.5f,rarityColor(armorRarity),true);
            txt("Tap left/right side to switch collected gear",124,318,6.5f,color("#777493"),false);
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
            rect(l,t,r,b,color("#1B1A35"),10);
            if(type==0&&weaponArt[weaponId]!=null)drawSprite(weaponArt[weaponId],l+5,t+4,24,32);
            else if(type==1&&armorArt[armorId]!=null)drawSprite(armorArt[armorId],l+5,t+4,24,32);
            else if(type==2&&caseArt[1]!=null)drawSprite(caseArt[1],l+5,t+4,24,32);
            else txt(icon,l+9,t+21,14,type==3?color("#FF9EBB"):color("#C6A6FF"),true);
            txt(name,l+30,t+19,8,Color.WHITE,true);txt(desc,l+9,t+35,7,color("#A5A1C4"),false);
            rect(r-58,t+43,r-7,b-5,color("#393057"),6);center(type==3?"BUY":cost+" G",r-32,t+57,7,color("#F0D48C"),true);
        }
        String regionName(){String[] n={"THE VEIL","ASHEN HOLLOW","FROSTBOUND","SUNKEN CITADEL","STARFALL MARCH","THE ABYSS"};return n[Math.floorMod(region,n.length)];}
        boolean realmUnlocked(int r){int[] req={1,5,10,16,22,30};if(r==0)return true;if(level<req[r])return false;for(int i=0;i<r;i++)if(regionBosses[i]<1)return false;return true;}
        String[] weaponNames(){return new String[]{"Riftfang","Mooncleaver","Sunspike","Frostbrand","Ashen Katana","Voidreaver","Crownpiercer","Starfall Edge"};}
        String[] armorNames(){return new String[]{"Warden Coat","Ashguard","Frostplate","Tidebound Mail","Astral Mantle","Hollow Aegis","Riftwalker Suit","Eclipse Crown"};}
        String rarityName(int r){String[] n={"COMMON","UNCOMMON","RARE","EPIC","LEGENDARY","MYTHIC"};return n[Math.max(0,Math.min(5,r))];}
        int rarityColor(int r){int[] a={color("#B6B8C8"),color("#72D6A3"),color("#69B7FF"),color("#C18BFF"),color("#F0C96E"),color("#FF6FB1")};return a[Math.max(0,Math.min(5,r))];}
        void chestArt(float x,float y,float scale,int rarity,boolean open){
            c.save();c.translate(x,y);c.scale(scale,scale);int rc=rarityColor(rarity);glow(0,8,27,rc,13);
            Bitmap art=caseArt[Math.max(0,Math.min(2,rarity-1))];
            if(art!=null){drawSprite(art,-32,-38,64,76);if(open){glow(0,-20,15,rc,14);for(int i=0;i<9;i++){float a=(i*41+System.currentTimeMillis()/10)%360;paint(Color.argb(230,255,239,188));c.drawCircle((float)Math.cos(Math.toRadians(a))*(10+i%4*6),-20+(float)Math.sin(Math.toRadians(a))*(9+i%3*4),1.5f+(i%2),p);}}c.restore();return;}
            paint(color("#151324"));Path base=new Path();base.moveTo(-27,-2);base.lineTo(27,-2);base.lineTo(23,22);base.lineTo(-23,22);base.close();c.drawPath(base,p);gradient(-27,-13,27,7,color("#5D402E"),color("#201B33"),5);paint(color("#A77D4E"));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawRoundRect(-27,-14,27,9,5,5,p);p.setStyle(Paint.Style.FILL);rect(-5,-14,5,22,rc,2);rect(-24,4,24,8,color("#D6B16D"),2);if(open){glow(0,-18,14,rc,15);}c.restore();
        }
        void cases(){
            txt("RELIC CASES",18,168,15,Color.WHITE,true);txt("Every opening is permanent loot • duplicates become gold",18,186,8,color("#9792BB"),false);
            int[] co={color("#77798A"),color("#4B9FC4"),color("#A84FC7")};String[] names={"WANDERER CACHE","ASTRAL VAULT","ECLIPSE CASE"};String[] cost={"120 GOLD","1 SHARD","300 GOLD + 2 SHARDS"};String[] detail={"Common → Epic","Rare → Mythic","Epic → Mythic"};
            for(int i=0;i<3;i++){int l=18+i*119;int rr=l+111;rect(l,202,rr,506,color("#17172F"),12);outline(l,202,rr,506,co[i],12,1);chestArt(l+55,283,1.0f,i+1,false);center(names[i],l+55,337,7.5f,Color.WHITE,true);center(cost[i],l+55,356,7,color("#F0D58F"),true);center(detail[i],l+55,375,7,color("#A6A0C6"),false);center(new String[]{"Gear roll","Better odds","Best odds"}[i],l+55,405,8,co[i],true);rect(l+8,455,rr-8,489,co[i],8);center("OPEN CASE",l+55,476,8,Color.WHITE,true);}
            rect(18,519,372,576,color("#1B1933"),10);txt("COLLECTION",30,538,9,color("#CBAFFF"),true);txt("Weapons "+Integer.bitCount(weaponCollection)+"/8",30,555,9,Color.WHITE,true);txt("Armor "+Integer.bitCount(armorCollection)+"/8",157,555,9,Color.WHITE,true);txt("Cases opened: "+casesOpened,276,555,8,color("#F0D58F"),true);
            rect(18,587,372,715,color("#111324"),11);if(System.currentTimeMillis()<lootNoticeUntil){chestArt(52,649,0.72f,3,true);txt("LATEST DROP",85,613,8,color("#BCA5F5"),true);txt(lootNotice,85,635,10,Color.WHITE,true);txt(lastLoot,85,653,8,color("#B2AACB"),false);txt("Gear stays in your collection. Tap HERO to equip.",30,690,8,color("#9C97B9"),false);}else{chestArt(52,649,0.72f,2,false);txt("LOOT REVEAL",85,613,9,color("#BCA5F5"),true);txt("Open a case to find rare weapons",85,634,10,Color.WHITE,true);txt("and armor with permanent stat bonuses.",85,651,9,color("#B2AACB"),false);txt("Duplicates are automatically dismantled for gold.",30,690,8,color("#9C97B9"),false);}
        }
        void openCase(int type){
            int costGold=type==0?120:type==1?0:300;int costShards=type==0?0:type==1?1:2;
            if(gold<costGold||shards<costShards){lootNotice="Not enough gold or shards";lootNoticeUntil=System.currentTimeMillis()+2500;play(sClick);return;}
            gold-=costGold;shards-=costShards;casesOpened++;
            int roll=rng.nextInt(100), rarity;
            if(type==0)rarity=roll<50?0:roll<77?1:roll<91?2:roll<98?3:4;
            else if(type==1)rarity=roll<25?1:roll<60?2:roll<85?3:roll<97?4:5;
            else rarity=roll<5?2:roll<30?3:roll<75?4:5;
            boolean weapon=rng.nextBoolean();int id=rng.nextInt(8);int power=2+rarity*5+rng.nextInt(4)+region*2;power=Math.max(power,weapon?ownedWeaponPower[id]:ownedArmorPower[id]);
            power=Math.max(power,weapon?ownedWeaponPower[id]:ownedArmorPower[id]);
            if(weapon){weaponCollection|=(1<<id);ownedWeaponPower[id]=Math.max(ownedWeaponPower[id],power);ownedWeaponRarity[id]=Math.max(ownedWeaponRarity[id],rarity);prefs.edit().putInt("wp"+id,ownedWeaponPower[id]).putInt("wr"+id,ownedWeaponRarity[id]).apply();
                if(weaponId==id||power>weaponPower){weaponId=id;weaponPower=power;weaponRarity=ownedWeaponRarity[id];lootNotice="EQUIPPED "+weaponNames()[id];}else{gold+=20+power*3;lootNotice="DISMANTLED "+weaponNames()[id];}}
            else{armorCollection|=(1<<id);ownedArmorPower[id]=Math.max(ownedArmorPower[id],power);ownedArmorRarity[id]=Math.max(ownedArmorRarity[id],rarity);prefs.edit().putInt("ap"+id,ownedArmorPower[id]).putInt("ar"+id,ownedArmorRarity[id]).apply();
                if(armorId==id||power>armorPower){armorId=id;armorPower=power;armorRarity=ownedArmorRarity[id];lootNotice="EQUIPPED "+armorNames()[id];}else{gold+=20+power*3;lootNotice="DISMANTLED "+armorNames()[id];}}
            lastLoot=rarityName(rarity)+" • +"+power+(weapon?" ATK":" DEF");lootNoticeUntil=System.currentTimeMillis()+5000;flashTicks=8;play(sWin);save();invalidate();
        }
        void cycleGear(boolean weapon){
            if(weapon){for(int step=1;step<=8;step++){int id=(weaponId+step)%8;if((weaponCollection&(1<<id))!=0){weaponId=id;weaponPower=ownedWeaponPower[id];weaponRarity=ownedWeaponRarity[id];break;}}}
            else{for(int step=1;step<=8;step++){int id=(armorId+step)%8;if((armorCollection&(1<<id))!=0){armorId=id;armorPower=ownedArmorPower[id];armorRarity=ownedArmorRarity[id];break;}}}
            play(sBuy);save();invalidate();
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
            txt("REALM ATLAS",18,168,15,Color.WHITE,true);txt("6 regions • guardian kills and levels unlock the path",18,186,8,color("#9792BB"),false);
            String[] names={"THE VEIL","ASHEN HOLLOW","FROSTBOUND","SUNKEN CITADEL","STARFALL MARCH","THE ABYSS"};
            String[] desc={"Lv. 1 • Broken ruins","Lv. 5 • Cinder fields","Lv. 10 • Frozen wastes","Lv. 16 • Drowned kingdom","Lv. 22 • Astral frontier","Lv. 30 • End of reality"};
            int[] tones={color("#6550A1"),color("#9B4E36"),color("#3B7398"),color("#277D83"),color("#7659B7"),color("#99365D")};
            for(int i=0;i<6;i++)realmCard(18,199+i*86,372,277+i*86,i,names[i],desc[i],tones[i],realmUnlocked(i));
            rect(18,718,372,729,color("#17172F"),5);txt("RAID VICTORIES "+bossKills+"   •   NEXT GATE "+(bossKills+1),27,726,7,color("#BBA4F4"),true);
        }
        void realmCard(int l,int t,int r,int b,int idx,String name,String desc,int co,boolean unlocked){
            gradient(l,t,r,b,co,color("#111322"),9);outline(l,t,r,b,unlocked?co:color("#353247"),9,1);
            if(realmArt[idx]!=null){drawSprite(realmArt[idx],r-84,t+4,80,68);rect(r-84,t+4,r-4,t+72,Color.argb(38,4,4,16),6);outline(r-84,t+4,r-4,t+72,co,6,1);}
            else{glow(r-42,t+33,19,co,10);paint(Color.argb(105,0,0,0));Path mountain=new Path();mountain.moveTo(r-93,t+60);mountain.lineTo(r-74,t+22);mountain.lineTo(r-55,t+43);mountain.lineTo(r-36,t+14);mountain.lineTo(r-8,t+60);mountain.close();c.drawPath(mountain,p);}
            txt("0"+(idx+1),l+11,t+18,7,color("#E0CBFF"),true);txt(name,l+34,t+20,9.5f,Color.WHITE,true);txt(desc,l+34,t+36,7,color("#D2C9E8"),false);
            rect(l+34,t+44,l+127,t+65,unlocked?color("#493B73"):color("#27243A"),6);center(unlocked?"ENTER REALM":"LOCKED",l+80,t+58,6.5f,unlocked?Color.WHITE:color("#77738F"),true);
            center(!unlocked?"BOSS GATE":(regionKills[idx]>0&&regionKills[idx]%10==0&&regionBosses[idx]<regionKills[idx]/10?"BOSS READY":"WAVE "+(regionKills[idx]%10)+"/10"),r-129,t+65,6,color(unlocked?"#D7F7D9":"#B1AEC5"),true);
        }
        void nav(){
            rect(0,735,390,844,color("#0A0B17"),0);rect(18,744,372,745,color("#282640"),1);
            String[] labels={"BATTLE","HERO","SKILLS","CASES","QUESTS","WORLD"};String[] icons={"⚔","♙","✧","▣","☷","⌖"};
            for(int i=0;i<6;i++){float x=32.5f+i*65;int co=tab==i?color("#D0ACFF"):color("#777691");if(tab==i)rect(x-23,750,x+23,806,color("#29223F"),9);center(icons[i],x,772,17,co,true);center(labels[i],x,792,6.4f,co,true);}
            center("PROJECT: ASCENSION  •  OFFLINE DARK RPG",195,825,7,color("#555471"),true);
            txt(audioOn?"♪ ON":"♪ OFF",333,824,7,audioOn?color("#9EEBFF"):color("#77718A"),true);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            float x=e.getX()/sx,y=e.getY()/sy;
            if(y>=735){if(x>=330){toggleAudio();}else{tab=Math.min(5,Math.max(0,(int)(x/65)));play(sClick);}invalidate();return true;}
            if(tab==0){
                if(y>=507&&y<=570){if(x<90){heroX=Math.max(52,heroX-34);lastStep=System.currentTimeMillis();}else if(x<160){jumpUntil=System.currentTimeMillis()+680;heroX=Math.min(322,heroX+8);play(sSkill);}else if(x<278)attackEnemy();else{heroX=Math.min(322,heroX+34);lastStep=System.currentTimeMillis();} }
                else if(y>=580&&y<=636){if(x<137)riftBurst();else if(x<255)usePotion();else tab=3;}
                else if(y>=646&&y<=715&&kills-questClaimed>=5){gold+=90;shards++;questClaimed+=5;play(sWin);save();}
            }else if(tab==1){
                if(y>=181&&y<=323){if(x<195)cycleGear(true);else cycleGear(false);}
                else if(y>=560&&y<=629){if(x<184)buyUpgrade(0);else buyUpgrade(1);}
                else if(y>=640&&y<=709){if(x<184)buyUpgrade(2);else buyUpgrade(3);}
            }else if(tab==2){
                if(y>=280&&y<=367)upgradeSkill(0);
                else if(y>=378&&y<=465)upgradeSkill(1);
                else if(y>=476&&y<=563)upgradeSkill(2);
                else if(y>=574&&y<=661)upgradeSkill(3);
            }else if(tab==3){
                if(y>=445&&y<=505){if(x<137)openCase(0);else if(x<255)openCase(1);else openCase(2);}
                else if(y>=507&&y<=570){if(x<137)openCase(0);else if(x<255)openCase(1);else openCase(2);}
            }else if(tab==4){
                if(y>=199&&y<=303&&eventProgress>=10&&eventClaimed==0){gold+=180;shards+=2;eventClaimed=1;play(sWin);save();}
                else if(y>=316&&y<=407&&kills-questClaimed>=5){gold+=90;shards++;questClaimed+=5;play(sWin);save();}
                else if(y>=420&&y<=511&&bossKills>prefs.getInt("claimedBossKills",0)){shards+=2;prefs.edit().putInt("claimedBossKills",bossKills).apply();gold+=120;play(sWin);save();}
                else if(y>=524&&y<=615&&shards>=3){shards-=3;potions++;play(sBuy);save();}
                else if(y>=628&&y<=715&&kills>0&&dailyClaimed==0){dailyClaimed=1;gold+=55;potions++;play(sWin);save();}
            }else if(tab==5){
                if(y>=199&&y<279&&realmUnlocked(0)){region=0;spawnEnemy();}
                else if(y>=285&&y<365&&realmUnlocked(1)){region=1;spawnEnemy();}
                else if(y>=371&&y<451&&realmUnlocked(2)){region=2;spawnEnemy();}
                else if(y>=457&&y<537&&realmUnlocked(3)){region=3;spawnEnemy();}
                else if(y>=543&&y<623&&realmUnlocked(4)){region=4;spawnEnemy();}
                else if(y>=629&&y<709&&realmUnlocked(5)){region=5;spawnEnemy();}
            }
            invalidate();return true;
        }
        void attackEnemy(){
            long now=System.currentTimeMillis();if(now-lastAttackAt<280)return;lastAttackAt=now;
            if(Math.abs(enemyX-heroX)>128){combatNotice="TOO FAR — MOVE CLOSER";combatNoticeUntil=now+1100;play(sClick);invalidate();return;}
            combo=Math.min(99,combo+1);hitTicks=10;shakeTicks=4;mana=Math.min(maxMana,mana+4+focus);
            boolean critical=rng.nextInt(100)<Math.min(70,crit+focus*2);
            int damage=actualAttack()+rng.nextInt(Math.max(4,actualAttack()/3+1));if(critical)damage=(int)(damage*1.85);
            if(combo%4==0)damage+=actualAttack()/2+strength*6;
            enemyHp-=damage;combatNotice=(critical?"CRITICAL  ":combo%4==0?"COMBO FINISH  ":"HIT  ")+damage;combatNoticeUntil=now+800;play(critical?sCrit:sHit);flashTicks=critical?5:2;
            if(enemyHp<=0){victory();}
            save();invalidate();
        }
        void victory(){
            kills++;stageWins++;if(boss)regionBosses[region]++;else regionKills[region]++;eventProgress=Math.min(10,eventProgress+1);gold+=boss?240+region*90:34+region*20;xp+=boss?420+level*35+region*80:55+level*10+region*30;
            if(rng.nextInt(100)<22||boss){shards+=boss?2:1;}
            if(rng.nextInt(100)<18){potions++;}
            if(boss){bossKills++;play(sWin);gold+=100+region*25;maxHp+=8;attack+=2;shards+=2;lootNotice="BOSS RELIC CACHE";lastLoot="Guaranteed boss reward";lootNoticeUntil=System.currentTimeMillis()+5000;}
            while(xp>=xpNeed()){xp-=xpNeed();level++;skillPoints++;maxHp+=16;maxMana+=12;mana=maxMana;hp=actualMaxHp();attack+=2;defense++;play(sLevel);flashTicks=7;}
            hp=Math.min(actualMaxHp(),hp+Math.max(8,actualMaxHp()/12));
            spawnEnemy();save();
        }
        void spawnEnemy(){
            boss=(regionKills[region]>0&&regionKills[region]%10==0&&regionBosses[region]<regionKills[region]/10);
            if(boss){String[] bn={"THE HOLLOW KING","EMBER COLOSSUS","FROST MOTHER","DROWNED ADMIRAL","ASTRAL DRAGON","THE UNMAKER"};enemyName=bn[Math.floorMod(region,bn.length)];enemyMax=430+level*48+region*160;enemyHp=enemyMax;play(sBoss);}
            else{enemyType=rng.nextInt(7);enemyName=enemyLabel(enemyType);enemyMax=90+level*18+region*60+enemyType*15;enemyHp=enemyMax;}
            heroX=105+rng.nextInt(32);enemyX=268+rng.nextInt(20);jumpUntil=0;nextEnemyAttack=System.currentTimeMillis()+1000;combo=0;save();
        }
        void riftBurst(){
            if(mana<25){combatNotice="NOT ENOUGH MANA";combatNoticeUntil=System.currentTimeMillis()+900;play(sClick);return;}
            if(Math.abs(enemyX-heroX)>150){combatNotice="RIFT BURST OUT OF RANGE";combatNoticeUntil=System.currentTimeMillis()+900;play(sClick);return;}
            mana-=25;hitTicks=10;int damage=(int)(actualAttack()*(1.75+strength*0.35));enemyHp-=damage;combatNotice="RIFT BURST  "+damage;combatNoticeUntil=System.currentTimeMillis()+1000;play(sSkill);flashTicks=6;
            hp=Math.min(actualMaxHp(),hp+vitality*4);
            if(enemyHp<=0)victory();save();invalidate();
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