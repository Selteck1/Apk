package com.selteck.ascension;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Random;

public class MainActivity extends Activity {
    private GameView game;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(7,8,15));
        getWindow().setNavigationBarColor(Color.rgb(7,8,15));
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512 | 4096);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        game=new GameView();
        setContentView(game);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus)getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512 | 4096);
    }
    @Override protected void onPause(){super.onPause();if(game!=null){game.save();game.pauseAudio();}}
    @Override protected void onResume(){super.onResume();if(game!=null)game.resumeAudio();}
    @Override protected void onDestroy(){if(game!=null)game.releaseAudio();super.onDestroy();}

    final class GameView extends View {
        static final int W=960,H=540, WORLD_W=12000,WORLD_H=8000, ZONE=4000;
        Canvas c; Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        float sx=1,sy=1,px=520,py=530,camX,camY,moveX,moveY,aimX,aimY,aimAngle=0.3f;
        int level=1,xp=0,xpNext=150,health=100,maxHealth=100,coins=80,kills=0,relaysTotal=0,shotsFired=0,shotsHit=0;
        int currentGun=0,medkits=2,ammoPickupCount=0,openedCaches=0,playerDamage=0,combo=0;
        int[] mag={12,0,0},reserve={96,0,0};
        boolean[] gunUnlocked={true,false,false},bossDefeated=new boolean[6],bossSpawned=new boolean[6];
        int[] relayCount=new int[6],zoneKills=new int[6];
        int selectedQuest=0,notificationColor=0;
        boolean aimActive=false,firing=false,mapOpen=false,paused=false,soundOn=true;
        String notice="Find the signal relays. Stay alive.",objectiveText="Find the first signal relay";
        long noticeUntil=0,lastUpdate=0,lastSave=0,lastSpawn=0,lastShot=0,reloadUntil=0,dashUntil=0,dashReadyAt=0,invincibleUntil=0,lastDamageAt=0,lastPickupAt=0;
        long firePressedAt=0,damageFlashUntil=0,questPulseUntil=0;
        int movePid=-1,aimPid=-1,firePid=-1;
        float moveTouchX=105,moveTouchY=430,aimTouchX=770,aimTouchY=430;
        float checkpointX=520,checkpointY=530;
        SharedPreferences prefs; Random rng=new Random(); ArrayList<Prop> props=new ArrayList<>(); ArrayList<Landmark> points=new ArrayList<>(); ArrayList<Mob> mobs=new ArrayList<>(); ArrayList<Shot> shots=new ArrayList<>(); ArrayList<Drop> drops=new ArrayList<>(); ArrayList<Spark> sparks=new ArrayList<>();
        Bitmap[] regionArt=new Bitmap[6], monsterArt=new Bitmap[7], bossArt=new Bitmap[6], caseArt=new Bitmap[3];
        Bitmap heroIdleArt,heroFireArt,heroDashArt;
        SoundPool soundPool; MediaPlayer ambient;
        int sPistol,sRifle,sShotgun,sHit,sBoss,sLoot,sDash,sQuest,sEmpty,sReload,sLevel;

        final class Prop {
            float x,y,r; int type,zone,variant; boolean solid;
            Prop(float xx,float yy,int t,float rr,int z,int v){x=xx;y=yy;type=t;r=rr;zone=z;variant=v;solid=t==0||t==1||t==2||t==4||t==6;}
        }
        final class Landmark {
            float x,y; int zone,type,id; String name; boolean used;
            Landmark(float xx,float yy,int z,int t,int i,String n){x=xx;y=yy;zone=z;type=t;id=i;name=n;}
        }
        final class Mob {
            float x,y,vx,vy,angle; int type,zone,hp,maxHp,damage; boolean big,alive=true;
            long nextAttack,stunUntil; float scale=1;
            Mob(float xx,float yy,int t,int z,boolean b){x=xx;y=yy;type=t;zone=z;big=b;scale=b?1.65f:0.9f;maxHp=b?900+level*75+z*240:44+level*5+z*19+t*12;hp=maxHp;damage=b?18+z*3:5+z*2+t;nextAttack=System.currentTimeMillis()+700+rng.nextInt(700);}
        }
        final class Shot {
            float x,y,vx,vy;int damage,life;boolean hostile;int type;
            Shot(float xx,float yy,float vx0,float vy0,int d,boolean h,int t){x=xx;y=yy;vx=vx0;vy=vy0;damage=d;hostile=h;type=t;life=h?100:70;}
        }
        final class Drop {
            float x,y;int type,value,zone;long born=System.currentTimeMillis();
            Drop(float xx,float yy,int t,int v,int z){x=xx;y=yy;type=t;value=v;zone=z;}
        }
        final class Spark {
            float x,y,vx,vy,size;int color,life,maxLife;
            Spark(float xx,float yy,float dx,float dy,float sz,int co,int lf){x=xx;y=yy;vx=dx;vy=dy;size=sz;color=co;life=lf;maxLife=lf;}
        }

        GameView(){
            super(MainActivity.this);
            prefs=getSharedPreferences("ascension_frontier_save",MODE_PRIVATE);
            loadArt();makeLandmarks();generateProps();loadGame();initAudio();
            setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            setFocusable(true);setContentDescription("ASCENSION FRONTIER top down exploration shooter");
            for(int z=0;z<6;z++)if(bossSpawned[z]&&!bossDefeated[z])spawnGuardian(z);
            for(int i=0;i<7;i++)spawnMobAroundPlayer(false);
            lastUpdate=System.currentTimeMillis();postInvalidateDelayed(33);
        }
        int col(String s){return Color.parseColor(s);}
        void paint(int co){p.reset();p.setAntiAlias(true);p.setFilterBitmap(true);p.setColor(co);}
        void box(float l,float t,float r,float b,int co,float rad){paint(co);c.drawRoundRect(l,t,r,b,rad,rad,p);}
        void stroke(float l,float t,float r,float b,int co,float rad,float sw){paint(co);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(sw);c.drawRoundRect(l,t,r,b,rad,rad,p);p.setStyle(Paint.Style.FILL);}
        void line(float x1,float y1,float x2,float y2,int co,float sw){paint(co);p.setStrokeWidth(sw);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x1,y1,x2,y2,p);}
        void text(String s,float x,float y,float size,int co,boolean bold){paint(co);p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));p.setShadowLayer(2,0,1,Color.argb(160,0,0,0));c.drawText(s,x,y,p);p.clearShadowLayer();}
        void centre(String s,float x,float y,float size,int co,boolean bold){paint(co);p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));p.setShadowLayer(2,0,1,Color.argb(180,0,0,0));c.drawText(s,x-p.measureText(s)/2,y,p);p.clearShadowLayer();}
        void circle(float x,float y,float r,int co){paint(co);c.drawCircle(x,y,r,p);}
        void glow(float x,float y,float r,int co,float blur){paint(co);p.setMaskFilter(new BlurMaskFilter(blur,BlurMaskFilter.Blur.NORMAL));c.drawCircle(x,y,r,p);p.setMaskFilter(null);}
        void fillGradient(float l,float t,float r,float b,int a,int z,float radius){paint(Color.WHITE);p.setShader(new LinearGradient(l,t,r,b,a,z,Shader.TileMode.CLAMP));c.drawRoundRect(l,t,r,b,radius,radius,p);p.setShader(null);}

        void loadArt(){
            try{
                Bitmap w=loadVector(R.drawable.art_worlds,390,1500);for(int i=0;i<6;i++)regionArt[i]=crop(w,0,i*250,390,250);
                Bitmap h=loadVector(R.drawable.art_heroes,96,384);heroIdleArt=crop(h,0,0,96,128);heroFireArt=crop(h,0,128,96,128);heroDashArt=crop(h,0,256,96,128);
                Bitmap e=loadVector(R.drawable.art_enemies,384,512);for(int i=0;i<7;i++)monsterArt[i]=crop(e,(i%4)*96,(i/4)*128,96,128);for(int i=0;i<6;i++){int j=7+i;bossArt[i]=crop(e,(j%4)*96,(j/4)*128,96,128);}
                Bitmap it=loadVector(R.drawable.art_items,400,384);for(int i=0;i<3;i++){int j=16+i;caseArt[i]=crop(it,(j%5)*80,(j/5)*96,80,96);}
            }catch(Exception ignored){}
        }
        Bitmap loadVector(int id,int w,int h){try{Drawable d=getResources().getDrawable(id).mutate();Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas old=c;c=new Canvas(b);d.setBounds(0,0,w,h);d.draw(c);c=old;return b;}catch(Exception e){return null;}}
        Bitmap crop(Bitmap b,int x,int y,int w,int h){try{return b==null?null:Bitmap.createBitmap(b,x,y,w,h);}catch(Exception e){return null;}}
        void sprite(Bitmap b,float x,float y,float w,float h){if(b==null)return;paint(Color.WHITE);p.setFilterBitmap(true);c.drawBitmap(b,null,new RectF(x,y,x+w,y+h),p);}
        int zoneAt(float x,float y){int cx=Math.max(0,Math.min(2,(int)(x/ZONE)));int row=y<ZONE?0:1;return row==0?cx:5-cx;}
        int currentZone(){return zoneAt(px,py);}
        String zoneName(int z){String[] a={"MOSSWOOD OUTSKIRTS","EMBERFALL","FROST VEIL","SUNKEN CITADEL","ASTRAL WASTES","THE LAST VEIL"};return a[Math.max(0,Math.min(5,z))];}
        int accent(int z){int[] a={col("#7DDCA2"),col("#F29B5D"),col("#90DFFF"),col("#55D7C4"),col("#B69AFF"),col("#FF6C9F")};return a[Math.floorMod(z,6)];}
        int groundColor(int z){int[] a={col("#253C31"),col("#493129"),col("#243B4B"),col("#1E3F42"),col("#312849"),col("#321C32")};return a[Math.floorMod(z,6)];}
        int terrainNoise(int x,int y){int n=x*374761393+y*668265263+0x27d4eb2d;n=(n^(n>>>13))*1274126177;return (n^(n>>>16))&0x7fffffff;}
        int shade(int color,int amount){return Color.rgb(Math.max(0,Math.min(255,Color.red(color)+amount)),Math.max(0,Math.min(255,Color.green(color)+amount)),Math.max(0,Math.min(255,Color.blue(color)+amount)));}
        void makeLandmarks(){
            for(int z=0;z<6;z++){
                int row=z<3?0:1,gridCol=row==0?z:5-z;float ox=gridCol*ZONE,oy=row*ZONE;
                points.add(new Landmark(ox+360,oy+430,z,3,points.size(),"FIELD CAMP "+(z+1)));
                points.add(new Landmark(ox+700,oy+780,z,0,points.size(),"SIGNAL RELAY A"));
                points.add(new Landmark(ox+3260,oy+920,z,0,points.size(),"SIGNAL RELAY B"));
                points.add(new Landmark(ox+850,oy+3150,z,0,points.size(),"SIGNAL RELAY C"));
                points.add(new Landmark(ox+3200,oy+3180,z,0,points.size(),"SIGNAL RELAY D"));
                points.add(new Landmark(ox+2050,oy+630,z,1,points.size(),"SUPPLY CACHE"));
                points.add(new Landmark(ox+1960,oy+3250,z,1,points.size(),"HIDDEN CACHE"));
                points.add(new Landmark(ox+2040,oy+1990,z,2,points.size(),"GUARDIAN ALTAR"));
            }
        }
        void generateProps(){
            Random r=new Random(0x415343454e444L);
            for(int i=0;i<1180;i++){
                float x=80+r.nextFloat()*(WORLD_W-160),y=80+r.nextFloat()*(WORLD_H-160);int z=zoneAt(x,y);
                boolean near=false;for(Landmark q:points)if(Math.hypot(q.x-x,q.y-y)<135){near=true;break;}if(near)continue;
                double pick=r.nextDouble();int type;
                if(z==0)type=pick<0.40?0:pick<0.55?3:pick<0.72?1:pick<0.85?5:pick<0.94?4:6;
                else if(z==1)type=pick<0.18?0:pick<0.36?2:pick<0.57?1:pick<0.72?5:pick<0.90?4:6;
                else if(z==2)type=pick<0.30?0:pick<0.49?1:pick<0.65?3:pick<0.81?4:pick<0.91?5:6;
                else if(z==3)type=pick<0.30?0:pick<0.50?3:pick<0.66?1:pick<0.80?5:pick<0.91?4:6;
                else if(z==4)type=pick<0.24?0:pick<0.40?1:pick<0.62?4:pick<0.80?5:pick<0.92?6:2;
                else type=pick<0.25?2:pick<0.44?4:pick<0.64?1:pick<0.80?5:pick<0.91?6:0;
                float radius=type==0?28:type==1?22:type==2?25:type==4?26:type==6?33:14;
                props.add(new Prop(x,y,type,radius,z,r.nextInt(5)));
            }
        }
        void loadGame(){
            px=prefs.getFloat("px",520);py=prefs.getFloat("py",530);checkpointX=prefs.getFloat("cx",px);checkpointY=prefs.getFloat("cy",py);
            level=Math.max(1,prefs.getInt("lv",1));xp=prefs.getInt("xp",0);xpNext=needXp();health=prefs.getInt("hp",100);maxHealth=prefs.getInt("mhp",100);health=Math.max(1,Math.min(maxHealth,health));coins=prefs.getInt("coin",80);kills=prefs.getInt("kills",0);relaysTotal=prefs.getInt("relays",0);openedCaches=prefs.getInt("caches",0);medkits=prefs.getInt("med",2);shotsFired=prefs.getInt("shotsF",0);shotsHit=prefs.getInt("shotsH",0);playerDamage=prefs.getInt("bounty",0);
            currentGun=prefs.getInt("gun",0);for(int i=0;i<3;i++){mag[i]=prefs.getInt("mag"+i,i==0?12:0);reserve[i]=prefs.getInt("res"+i,i==0?96:0);gunUnlocked[i]=prefs.getBoolean("g"+i,i==0);}
            for(int i=0;i<points.size();i++)points.get(i).used=prefs.getBoolean("p"+i,false);
            for(int z=0;z<6;z++){relayCount[z]=prefs.getInt("relay"+z,0);zoneKills[z]=prefs.getInt("zk"+z,0);bossDefeated[z]=prefs.getBoolean("bd"+z,false);bossSpawned[z]=prefs.getBoolean("bs"+z,false);}
            if(px<0||px>WORLD_W||py<0||py>WORLD_H){px=520;py=530;}
            if(currentGun<0||currentGun>2||!gunUnlocked[currentGun])currentGun=0;
            notification("Welcome back, survivor. The frontier is waiting.",3000);
        }
        int needXp(){return 135+level*72+level*level*5;}
        void save(){
            SharedPreferences.Editor e=prefs.edit().putFloat("px",px).putFloat("py",py).putFloat("cx",checkpointX).putFloat("cy",checkpointY).putInt("lv",level).putInt("xp",xp).putInt("hp",health).putInt("mhp",maxHealth).putInt("coin",coins).putInt("kills",kills).putInt("relays",relaysTotal).putInt("caches",openedCaches).putInt("med",medkits).putInt("shotsF",shotsFired).putInt("shotsH",shotsHit).putInt("bounty",playerDamage).putInt("gun",currentGun);
            for(int i=0;i<3;i++)e.putInt("mag"+i,mag[i]).putInt("res"+i,reserve[i]).putBoolean("g"+i,gunUnlocked[i]);
            for(int i=0;i<points.size();i++)e.putBoolean("p"+i,points.get(i).used);
            for(int z=0;z<6;z++)e.putInt("relay"+z,relayCount[z]).putInt("zk"+z,zoneKills[z]).putBoolean("bd"+z,bossDefeated[z]).putBoolean("bs"+z,bossSpawned[z]);
            e.apply();
        }
        void notification(String s,int ms){notice=s;noticeUntil=System.currentTimeMillis()+ms;}
        void addXP(int amount){xp+=amount;while(xp>=needXp()){xp-=needXp();level++;maxHealth+=9;health=Math.min(maxHealth,health+34);notification("LEVEL UP  •  LEVEL "+level+"  •  MAX HP +9",2600);play(sLevel);for(int i=0;i<24;i++)particle(px,py,accent(currentZone()),2.8f,20); }xpNext=needXp();}
        boolean regionUnlocked(int z){return z==0||bossDefeated[z-1];}
        int relaysIn(int z){int n=0;for(Landmark q:points)if(q.zone==z&&q.type==0&&q.used)n++;return n;}
        boolean guardianAlive(){for(Mob m:mobs)if(m.big&&m.alive)return true;return false;}
        int killGoal(int z){return 60+25*z;}
        Landmark nearestLandmark(float x,float y,boolean actionableOnly){Landmark best=null;float bd=Float.MAX_VALUE;for(Landmark q:points){if(actionableOnly&&q.used&&q.type!=3)continue;float d=dist(x,y,q.x,q.y);if(d<bd){bd=d;best=q;}}return best;}
        float dist(float x1,float y1,float x2,float y2){return (float)Math.hypot(x1-x2,y1-y2);}
        void initAudio(){
            try{
                AudioAttributes aa=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
                soundPool=new SoundPool.Builder().setMaxStreams(10).setAudioAttributes(aa).build();
                sPistol=tone("pistol",170,0.075,0.32,true);sRifle=tone("rifle",125,0.07,0.26,true);sShotgun=tone("shotgun",82,0.17,0.42,true);
                sHit=tone("hit",250,0.10,0.25,true);sBoss=tone("boss",58,0.36,0.38,false);sLoot=tone("loot",680,0.21,0.26,false);sDash=tone("dash",310,0.14,0.22,true);sQuest=tone("quest",520,0.30,0.24,false);sEmpty=tone("empty",220,0.055,0.13,false);sReload=tone("reload",430,0.11,0.18,false);sLevel=tone("level",720,0.38,0.30,false);
                makeAmbient();
            }catch(Exception ignored){}
        }
        int tone(String name,double freq,double sec,double vol,boolean noise)throws Exception{
            int rate=22050,n=(int)(rate*sec);ByteBuffer b=ByteBuffer.allocate(44+n*2).order(ByteOrder.LITTLE_ENDIAN);
            b.put(new byte[]{'R','I','F','F'});b.putInt(36+n*2);b.put(new byte[]{'W','A','V','E','f','m','t',' '});b.putInt(16);b.putShort((short)1);b.putShort((short)1);b.putInt(rate);b.putInt(rate*2);b.putShort((short)2);b.putShort((short)16);b.put(new byte[]{'d','a','t','a'});b.putInt(n*2);
            for(int i=0;i<n;i++){double t=i/(double)rate,en=Math.min(1,t*90)*Math.pow(Math.max(0,1-t/sec),2.2);double wav=Math.sin(2*Math.PI*freq*t);if(noise)wav=wav*0.57+(rng.nextDouble()*2-1)*0.43;if(name.equals("shotgun"))wav=wav+0.35*Math.sin(2*Math.PI*freq*0.48*t);short v=(short)(Math.max(-1,Math.min(1,wav*en*vol))*32767);b.putShort(v);}
            File f=new File(getCacheDir(),"af_"+name+".wav");FileOutputStream o=new FileOutputStream(f);o.write(b.array());o.close();return soundPool.load(f.getAbsolutePath(),1);
        }
        void makeAmbient()throws Exception{
            int rate=22050,n=rate*9;ByteBuffer b=ByteBuffer.allocate(44+n*2).order(ByteOrder.LITTLE_ENDIAN);b.put(new byte[]{'R','I','F','F'});b.putInt(36+n*2);b.put(new byte[]{'W','A','V','E','f','m','t',' '});b.putInt(16);b.putShort((short)1);b.putShort((short)1);b.putInt(rate);b.putInt(rate*2);b.putShort((short)2);b.putShort((short)16);b.put(new byte[]{'d','a','t','a'});b.putInt(n*2);
            for(int i=0;i<n;i++){double t=i/(double)rate,env=Math.pow(Math.max(0,Math.sin(Math.PI*t/9)),0.4);double v=Math.sin(2*Math.PI*55*t)*0.18+Math.sin(2*Math.PI*82.4*t)*0.14+Math.sin(2*Math.PI*110*t+Math.sin(t*.35))*0.10+Math.sin(2*Math.PI*164.8*t)*0.06+Math.sin(2*Math.PI*41.2*t)*0.05*Math.sin(t*.7);b.putShort((short)(v*env*4500));}
            File f=new File(getCacheDir(),"af_ambience.wav");FileOutputStream o=new FileOutputStream(f);o.write(b.array());o.close();ambient=new MediaPlayer();ambient.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());ambient.setDataSource(f.getAbsolutePath());ambient.prepare();ambient.setLooping(true);ambient.setVolume(0.19f,0.19f);ambient.start();
        }
        void play(int id){if(soundOn&&soundPool!=null&&id!=0)try{soundPool.play(id,1,1,1,0,0.94f+rng.nextFloat()*0.12f);}catch(Exception ignored){}}
        void toggleSound(){soundOn=!soundOn;if(ambient!=null)try{if(soundOn){if(!ambient.isPlaying())ambient.start();}else if(ambient.isPlaying())ambient.pause();}catch(Exception ignored){}if(soundOn)play(sLoot);}
        void pauseAudio(){if(soundPool!=null)soundPool.autoPause();if(ambient!=null)try{if(ambient.isPlaying())ambient.pause();}catch(Exception ignored){}}
        void resumeAudio(){if(soundPool!=null)soundPool.autoResume();if(soundOn&&ambient!=null)try{if(!ambient.isPlaying())ambient.start();}catch(Exception ignored){}}
        void releaseAudio(){if(soundPool!=null){soundPool.release();soundPool=null;}if(ambient!=null){try{ambient.stop();}catch(Exception ignored){}ambient.release();ambient=null;}}

        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);c=canvas;sx=getWidth()/(float)W;sy=getHeight()/(float)H;c.save();c.scale(sx,sy);
            long now=System.currentTimeMillis();float dt=Math.max(0.001f,Math.min(0.05f,(now-lastUpdate)/1000f));lastUpdate=now;
            updateGame(dt,now);
            paint(col("#10131B"));c.drawRect(0,0,W,H,p);
            float aimCamX=Math.max(0,Math.min(WORLD_W-W,px-W/2f)),aimCamY=Math.max(0,Math.min(WORLD_H-H,py-H/2f));camX=aimCamX;camY=aimCamY;
            c.save();c.translate(-camX,-camY);
            drawTerrain();drawRoads();drawProps(false);drawLandmarks();drawDrops();drawShots();drawMobs();drawPlayer();drawProps(true);drawSparks();drawWorldEdges();
            c.restore();
            drawHUD(now);drawControls(now);if(mapOpen)drawFullMap();if(now<damageFlashUntil){box(0,0,W,H,Color.argb(42,255,38,74),0);}
            if(now<noticeUntil)drawNotice(now);
            c.restore();postInvalidateDelayed(33);
        }
        void drawTerrain(){
            int tile=80;int x0=(int)Math.floor(camX/tile)*tile,y0=(int)Math.floor(camY/tile)*tile;
            for(int y=y0;y<camY+H+tile;y+=tile)for(int x=x0;x<camX+W+tile;x+=tile){
                int z=zoneAt(x+tile/2f,y+tile/2f);int n=terrainNoise(x/tile,y/tile);int base=groundColor(z);int shadeAmt=(n%17)-8;int cc=shade(base,shadeAmt);paint(cc);c.drawRect(x,y,x+tile+1,y+tile+1,p);
                if((n%7)==0){paint(Color.argb(80,Color.red(accent(z)),Color.green(accent(z)),Color.blue(accent(z))));c.drawOval(x+13+(n%35),y+11+(n%27),x+26+(n%35),y+16+(n%27),p);}
                if((n%13)==0){paint(Color.argb(80,10,12,18));c.drawCircle(x+50,y+46,2.4f,p);}
                if(z==2&&(n%4==0)){paint(Color.argb(45,196,235,255));c.drawLine(x+10,y+63,x+36,y+58,p);}
                if(z==3&&(n%5==0)){paint(Color.argb(45,65,220,202));c.drawCircle(x+22,y+26,3,p);c.drawCircle(x+27,y+28,1.5f,p);}
                if(z==1&&(n%8==0)){paint(Color.argb(70,255,138,64));c.drawCircle(x+39,y+37,2,p);}
                if(z==4&&(n%6==0)){paint(Color.argb(85,198,153,255));Path d=new Path();d.moveTo(x+40,y+32);d.lineTo(x+43,y+37);d.lineTo(x+40,y+42);d.lineTo(x+37,y+37);d.close();c.drawPath(d,p);}
            }
            // subtle biome boundaries and trails through each zone
            for(int x=ZONE;x<WORLD_W;x+=ZONE){line(x,0,x,WORLD_H,Color.argb(35,206,197,255),3);}
            line(0,ZONE,WORLD_W,ZONE,Color.argb(35,206,197,255),3);
        }
        void drawRoads(){
            for(int z=0;z<6;z++){
                int row=z<3?0:1,gc=row==0?z:5-z;float ox=gc*ZONE,oy=row*ZONE,cx=ox+2000,cy=oy+2000;
                road(ox+360,oy+430,ox+700,oy+780,z);road(ox+360,oy+430,ox+3260,oy+920,z);road(ox+360,oy+430,ox+850,oy+3150,z);road(ox+360,oy+430,ox+3200,oy+3180,z);road(ox+360,oy+430,cx,cy,z);
                road(cx,cy,ox+2050,oy+630,z);road(cx,cy,ox+1960,oy+3250,z);
            }
        }
        void road(float x1,float y1,float x2,float y2,int z){
            if(Math.max(Math.abs((x1+x2)/2-camX),Math.abs((y1+y2)/2-camY))>1300)return;
            float mx=(x1+x2)/2+(y2-y1)*0.055f,my=(y1+y2)/2-(x2-x1)*0.055f;Path path=new Path();path.moveTo(x1,y1);path.quadTo(mx,my,x2,y2);
            paint(Color.argb(120,11,13,17));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(66);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);c.drawPath(path,p);
            paint(Color.argb(115, z==2?125:132,z==2?151:112,z==2?158:81));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(45);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
            paint(Color.argb(35,235,218,173));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setPathEffect(new DashPathEffect(new float[]{14,19},0));c.drawPath(path,p);p.setPathEffect(null);p.setStyle(Paint.Style.FILL);
        }
        boolean visible(float x,float y,float margin){return x>camX-margin&&x<camX+W+margin&&y>camY-margin&&y<camY+H+margin;}
        void drawProps(boolean foreground){
            long now=System.currentTimeMillis();
            for(Prop q:props){if(!visible(q.x,q.y,75))continue;if((q.y>py+10)!=foreground)continue;
                float x=q.x,y=q.y;int z=q.zone;int a=accent(z);
                if(q.type==0){ // living tree
                    glow(x,y+8,25,Color.argb(65,3,8,8),9);circle(x,y+2,8,col("#3B2E28"));circle(x-8,y-10,19,z==2?col("#52758C"):z==3?col("#1F6D69"):z==4?col("#51417A"):z==5?col("#572A4A"):z==1?col("#70503A"):col("#356448"));circle(x+8,y-16,17,z==2?col("#6F94A6"):z==3?col("#2A8078"):z==4?col("#675190"):z==5?col("#783451"):z==1?col("#915134"):col("#477A54"));circle(x-2,y-28,13,z==2?col("#7FA3B4"):z==3?col("#348F7F"):z==4?col("#785AA0"):z==5?col("#8A4261"):z==1?col("#AE6640"):col("#56895C"));circle(x-7,y-21,4,Color.argb(100,211,240,190));circle(x+13,y-4,3,Color.argb(90,245,220,173));
                }else if(q.type==1){ // rocks/crystals
                    glow(x,y,18,Color.argb(55,Color.red(a),Color.green(a),Color.blue(a)),7);
                    Path rock=new Path();rock.moveTo(x-q.r,y+9);rock.lineTo(x-q.r*.8f,y-9);rock.lineTo(x-4,y-q.r);rock.lineTo(x+q.r*.7f,y-q.r*.7f);rock.lineTo(x+q.r,y+7);rock.close();paint(z==2?col("#6A879B"):z==4?col("#655184"):z==5?col("#692C50"):col("#484957"));c.drawPath(rock,p);line(x-4,y-q.r+4,x+q.r*.55f,y-2,Color.argb(110,214,218,227),2);
                    if(z==4){Path cr=new Path();cr.moveTo(x+2,y-8);cr.lineTo(x+8,y-24);cr.lineTo(x+12,y-7);cr.close();paint(a);c.drawPath(cr,p);}
                }else if(q.type==2){ // dead tree
                    line(x,y+15,x+q.variant*2-4,y-22,col("#493C36"),8);line(x,y-3,x-17,y-18,col("#57433A"),5);line(x-2,y-8,x+18,y-25,col("#4E3C37"),5);line(x,y+5,x+14,y+4,col("#493A32"),4);
                    if(z==5){circle(x-14,y-19,4,a);circle(x+18,y-25,4,a);}
                }else if(q.type==3){circle(x,y,15,z==2?col("#5F7784"):col("#2E553A"));circle(x-5,y-5,9,z==2?col("#73939E"):col("#487044"));circle(x+8,y+3,8,z==2?col("#496978"):col("#315939"));}
                else if(q.type==4){ // ruined stone / crystal cluster
                    box(x-24,y-17,x+19,y+19,col("#20252D"),2);box(x-19,y-23,x+14,y+9,z==4?col("#5A4A79"):z==3?col("#315D5C"):col("#51505C"),2);line(x-14,y-15,x+8,y-15,Color.argb(110,205,212,219),2);line(x+7,y-14,x+7,y+3,Color.argb(100,208,216,216),2);
                    if(z==4||z==5){Path cp=new Path();cp.moveTo(x-2,y-20);cp.lineTo(x+6,y-39);cp.lineTo(x+11,y-17);cp.close();paint(a);c.drawPath(cp,p);glow(x+4,y-24,5,a,7);}
                }else if(q.type==5){box(x-11,y-15,x+12,y+14,col("#24262E"),4);box(x-9,y-12,x+10,y+9,z==1?col("#9A512F"):col("#73503B"),3);box(x-12,y-17,x+13,y-10,col("#C29A55"),2);line(x,y-8,x,y+7,col("#D8BA6A"),2);}
                else{ // ruined pillar
                    box(x-21,y-25,x+22,y+19,col("#171C29"),3);box(x-17,y-31,x+18,y+10,z==5?col("#6B2750"):col("#41485B"),3);line(x-11,y-20,x+11,y-20,a,2);line(x+5,y-15,x+5,y+3,a,2);
                }
            }
        }
        void drawLandmarks(){
            long t=System.currentTimeMillis();
            for(Landmark q:points){if(!visible(q.x,q.y,100))continue;float pulse=(float)(0.82+0.18*Math.sin(t/260.0+q.id));int a=accent(q.zone);boolean active=!q.used&&(q.type!=2||relaysIn(q.zone)>=4&&regionUnlocked(q.zone))&&(q.type!=0||regionUnlocked(q.zone));
                if(q.type==0){glow(q.x,q.y,25*pulse,active?a:col("#48505B"),9);circle(q.x,q.y+10,20,Color.argb(100,0,0,0));circle(q.x,q.y,13,col("#1F2932"));circle(q.x,q.y,8,active?a:col("#59636E"));line(q.x,q.y-26,q.x,q.y+8,active?col("#C8F9E2"):col("#717887"),3);circle(q.x,q.y-27,5,active?col("#D1FFE8"):col("#777B8C"));circle(q.x,ySafe(q.y-27),2,col("#FFFFFF"));
                    for(int k=0;k<3;k++){paint(Color.argb(active?115:35,Color.red(a),Color.green(a),Color.blue(a)));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.4f);float rr=24+k*9+(float)Math.sin(t/300.0+k)*3;c.drawCircle(q.x,q.y,rr,p);p.setStyle(Paint.Style.FILL);}
                }else if(q.type==1){glow(q.x,q.y,26*pulse,a,9);box(q.x-27,q.y-10,q.x+27,q.y+21,col("#171A25"),4);box(q.x-24,q.y-15,q.x+24,q.y+6,q.used?col("#55545C"):col("#76503B"),6);stroke(q.x-24,q.y-15,q.x+24,q.y+6,q.used?col("#777783"):a,6,2);box(q.x-4,q.y-11,q.x+4,q.y+19,col("#DDB968"),1);circle(q.x,q.y-4,3,a);}
                else if(q.type==2){glow(q.x,q.y,39*pulse,a,14);paint(Color.argb(130,Color.red(a),Color.green(a),Color.blue(a)));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);c.drawCircle(q.x,q.y,38,p);p.setStyle(Paint.Style.FILL);Path tri=new Path();tri.moveTo(q.x,q.y-33);tri.lineTo(q.x+29,q.y+20);tri.lineTo(q.x-29,q.y+20);tri.close();paint(col("#291D38"));c.drawPath(tri,p);stroke(q.x-16,q.y-17,q.x+16,q.y+17,a,2,2);line(q.x-14,q.y+12,q.x+14,q.y-12,a,2);circle(q.x,q.y,a==0?2:4,a);}
                else{glow(q.x,q.y,30,a,12);box(q.x-30,q.y-15,q.x+30,q.y+25,col("#1B2630"),7);Path tent=new Path();tent.moveTo(q.x-39,q.y-6);tent.lineTo(q.x,q.y-37);tent.lineTo(q.x+39,q.y-6);tent.close();paint(col("#647A76"));c.drawPath(tent,p);line(q.x,q.y-33,q.x,q.y+17,col("#C9B880"),3);circle(q.x+21,q.y+12,8,col("#FFAE57"));glow(q.x+21,q.y+12,10,col("#FF943A"),8);}
            }
        }
        float ySafe(float y){return y;}
        void drawWorldEdges(){
            // Tiny rune gate markers show progression locks on region borders.
            for(int i=1;i<3;i++){float x=i*ZONE;if(visible(x,2000,60)){line(x,0,x,WORLD_H,Color.argb(20,225,225,255),3);gate(x,2000,regionUnlocked(i));gate(x,6000,regionUnlocked(5-i));}}
            if(visible(6000,ZONE,60)){gate(10000,ZONE,regionUnlocked(3));}
            line(0,ZONE,WORLD_W,ZONE,Color.argb(20,235,235,255),3);
        }
        void gate(float x,float y,boolean open){if(Math.abs(x-camX)>W+80||Math.abs(y-camY)>H+80)return;int co=open?col("#65DDB1"):col("#EF6387");glow(x,y,32,co,15);line(x-25,y-22,x-25,y+22,co,4);line(x+25,y-22,x+25,y+22,co,4);for(int i=0;i<5;i++)circle(x-14+i*7,y+(float)Math.sin(System.currentTimeMillis()/150.0+i)*3,2,co);}

        void drawDrops(){
            long now=System.currentTimeMillis();
            for(Drop d:drops){if(!visible(d.x,d.y,45))continue;float bob=(float)Math.sin(now/180.0+d.x)*3;int a=accent(d.zone);glow(d.x,d.y,15,a,7);
                if(d.type==0){circle(d.x,d.y+bob,7,col("#E9C46A"));circle(d.x-2,d.y-2+bob,2,col("#FFF0AD"));}
                else if(d.type==1){box(d.x-9,d.y-10+bob,d.x+9,d.y+10+bob,col("#315F93"),4);line(d.x,d.y-6+bob,d.x,d.y+6+bob,col("#A9E8FF"),2);line(d.x-5,d.y+bob,d.x+5,d.y+bob,col("#A9E8FF"),2);}
                else if(d.type==2){glow(d.x,d.y,18,col("#64DD98"),10);circle(d.x,d.y+bob,8,col("#5CCB87"));centre("+",d.x,d.y+3+bob,12,Color.WHITE,true);}
                else if(d.type==3){Path shard=new Path();shard.moveTo(d.x,d.y-12+bob);shard.lineTo(d.x+8,d.y+bob);shard.lineTo(d.x,d.y+12+bob);shard.lineTo(d.x-7,d.y+bob);shard.close();paint(col("#D0A5FF"));c.drawPath(shard,p);glow(d.x,d.y,15,col("#B58AFF"),7);}
                else{if(caseArt[d.value%3]!=null)sprite(caseArt[d.value%3],d.x-17,d.y-18+bob,34,36);else box(d.x-14,d.y-11,d.x+14,d.y+12,col("#AE65E8"),3);text(d.value==1?"SMG":"12G",d.x-14,d.y+24+bob,8,Color.WHITE,true);}
            }
        }
        void drawShots(){
            for(Shot b:shots){if(!visible(b.x,b.y,20))continue;float len=b.hostile?13:21;float mag=(float)Math.hypot(b.vx,b.vy);float ex=b.x-b.vx/(mag+0.01f)*len,ey=b.y-b.vy/(mag+0.01f)*len;line(ex,ey,b.x,b.y,b.hostile?col("#FF7D81"):col("#FFDA86"),b.hostile?3:4);glow(b.x,b.y,4,b.hostile?col("#FF555F"):col("#FFBA56"),6);}
        }
        void drawMobs(){
            long now=System.currentTimeMillis();
            for(Mob m:mobs){if(!m.alive||!visible(m.x,m.y,90))continue;float bob=(float)Math.sin(now/160.0+m.x)*2;int a=accent(m.zone);circle(m.x,m.y+12,m.big?32:17,Color.argb(110,0,0,0));
                if(m.big)drawGuardian(m,bob,now);else drawMonster(m,bob,now);
                if(m.big||m.hp<m.maxHp){float w=m.big?100:35,top=m.y-(m.big?69:37);box(m.x-w/2-2,top-2,m.x+w/2+2,top+7,col("#11131B"),3);box(m.x-w/2,top,m.x+w/2,top+5,col("#5B2937"),2);box(m.x-w/2,top,m.x-w/2+w*Math.max(0,m.hp/(float)m.maxHp),top+5,m.big?col("#FE567E"):col("#E3A26B"),2);}
                if(m.big){centre(new String[]{"THE MOSS TITAN","CINDER BRUTE","FROST QUEEN","DROWNED WARDEN","STAR EATER","THE UNMAKER"}[m.zone],m.x,m.y-78,9,col("#FFD7ED"),true);}
            }
        }
        void drawGuardian(Mob m,float bob,long now){
            float x=m.x,y=m.y+bob;int a=accent(m.zone);circle(x,y+14,40,Color.argb(130,0,0,0));
            glow(x,y,45,Color.argb(100,Color.red(a),Color.green(a),Color.blue(a)),20);
            if(m.zone==0){
                for(int k=0;k<6;k++){float an=k*1.047f+(float)Math.sin(now/300.0)*0.12f;line(x+(float)Math.cos(an)*17,y+(float)Math.sin(an)*17,x+(float)Math.cos(an)*43,y+(float)Math.sin(an)*43,col("#426C4A"),8);circle(x+(float)Math.cos(an)*43,y+(float)Math.sin(an)*43,5,col("#8ACC78"));}
            } else if(m.zone==1){
                for(int k=0;k<4;k++){float an=k*1.57f;line(x+(float)Math.cos(an)*15,y+(float)Math.sin(an)*15,x+(float)Math.cos(an)*38,y+(float)Math.sin(an)*38,col("#9C4C30"),11);glow(x+(float)Math.cos(an)*38,y+(float)Math.sin(an)*38,6,col("#FF9E4B"),9);}
            } else if(m.zone==2){
                for(int k=0;k<6;k++){float an=k*1.047f;Path ice=new Path();ice.moveTo(x+(float)Math.cos(an)*15,y+(float)Math.sin(an)*15);ice.lineTo(x+(float)Math.cos(an)*36,y+(float)Math.sin(an)*36);ice.lineTo(x+(float)Math.cos(an+0.2f)*17,y+(float)Math.sin(an+0.2f)*17);ice.close();paint(col("#83D7F2"));c.drawPath(ice,p);}
            } else if(m.zone==3){
                for(int k=0;k<5;k++){float an=k*1.256f;line(x+(float)Math.cos(an)*17,y+(float)Math.sin(an)*17,x+(float)Math.cos(an)*43,y+(float)Math.sin(an)*43,col("#256E6F"),7);circle(x+(float)Math.cos(an)*42,y+(float)Math.sin(an)*42,5,col("#53DCC7"));}
            } else if(m.zone==4){
                for(int k=0;k<4;k++){float an=k*1.57f+0.4f;line(x+(float)Math.cos(an)*17,y+(float)Math.sin(an)*17,x+(float)Math.cos(an)*45,y+(float)Math.sin(an)*45,col("#62479C"),9);circle(x+(float)Math.cos(an)*45,y+(float)Math.sin(an)*45,7,col("#C5A5FF"));}
            } else{
                for(int k=0;k<8;k++){float an=k*0.785f;line(x+(float)Math.cos(an)*15,y+(float)Math.sin(an)*15,x+(float)Math.cos(an)*43,y+(float)Math.sin(an)*43,col("#7E2753"),8);circle(x+(float)Math.cos(an)*43,y+(float)Math.sin(an)*43,6,col("#FF6CA9"));}
            }
            circle(x,y,29,col("#171C2A"));circle(x,y,23,m.zone==2?col("#56829B"):col("#49324D"));circle(x,y,16,col("#252A39"));
            for(int k=0;k<3;k++){float an=k*2.094f+(float)Math.sin(now/390.0)*0.16f;circle(x+(float)Math.cos(an)*17,y+(float)Math.sin(an)*17,4,a);}
            circle(x,y,8,col("#0A101B"));circle(x-3,y-2,2,col("#FFB9CC"));circle(x+3,y-2,2,col("#FFB9CC"));
            paint(Color.argb(85,Color.red(a),Color.green(a),Color.blue(a)));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawCircle(x,y,51+(float)Math.sin(now/180.0)*2,p);p.setStyle(Paint.Style.FILL);
        }
        void drawMonster(Mob m,float bob,long now){
            float x=m.x,y=m.y+bob;int base=m.type==0?col("#4B8760"):m.type==1?col("#A45A46"):m.type==2?col("#467F9A"):m.type==3?col("#55466F"):m.type==4?col("#8C613D"):m.type==5?col("#536A7A"):col("#743E6F");
            glow(x,y,17,Color.argb(75,Color.red(accent(m.zone)),Color.green(accent(m.zone)),Color.blue(accent(m.zone))),9);
            if(m.type==0){circle(x-9,y+4,8,base);circle(x+8,y+4,8,base);circle(x,y-5,13,base);circle(x-8,y-13,6,base);circle(x+8,y-13,6,base);circle(x-4,y-5,2,col("#FFDB8C"));circle(x+4,y-5,2,col("#FFDB8C"));}
            else if(m.type==1){Path body=new Path();body.moveTo(x-17,y+4);body.lineTo(x-12,y-12);body.lineTo(x-4,y-20);body.lineTo(x+8,y-15);body.lineTo(x+18,y-1);body.lineTo(x+11,y+13);body.lineTo(x-10,y+13);body.close();paint(base);c.drawPath(body,p);line(x-10,y-7,x-17,y-20,col("#BC8A70"),4);line(x+10,y-7,x+17,y-20,col("#BC8A70"),4);circle(x-5,y-6,2,col("#FFB46B"));circle(x+5,y-6,2,col("#FFB46B"));}
            else if(m.type==2){Path body=new Path();body.moveTo(x,y-20);body.lineTo(x+16,y-8);body.lineTo(x+11,y+11);body.lineTo(x,y+17);body.lineTo(x-11,y+11);body.lineTo(x-16,y-8);body.close();paint(base);c.drawPath(body,p);circle(x,y-3,7,col("#9DEBFA"));circle(x-4,y-4,2,Color.WHITE);circle(x+4,y-4,2,Color.WHITE);}
            else if(m.type==3){circle(x,y,17,base);Path horns=new Path();horns.moveTo(x-10,y-8);horns.lineTo(x-19,y-24);horns.lineTo(x-6,y-17);horns.close();horns.moveTo(x+10,y-8);horns.lineTo(x+19,y-24);horns.lineTo(x+6,y-17);horns.close();paint(col("#B6A7CE"));c.drawPath(horns,p);line(x-5,y-2,x-2,y-2,Color.WHITE,3);line(x+3,y-2,x+6,y-2,Color.WHITE,3);}
            else if(m.type==4){circle(x,y,15,base);circle(x,y-3,8,col("#C7A06C"));line(x-11,y+5,x-19,y+13,col("#BD8960"),4);line(x+11,y+5,x+19,y+13,col("#BD8960"),4);circle(x-3,y-5,2,col("#211823"));circle(x+3,y-5,2,col("#211823"));}
            else if(m.type==5){box(x-16,y-15,x+16,y+15,base,7);stroke(x-16,y-15,x+16,y+15,col("#9ACDDA"),7,2);line(x-9,y-7,x+9,y+7,col("#9ACDDA"),2);line(x+9,y-7,x-9,y+7,col("#9ACDDA"),2);circle(x,y,4,col("#F1D58F"));}
            else{circle(x,y,15,base);for(int i=0;i<5;i++){float an=i*1.256f+(float)Math.sin(now/230.0)*0.2f;circle(x+(float)Math.cos(an)*13,y+(float)Math.sin(an)*13,5,col("#B66AC5"));}circle(x,y,8,col("#24152E"));circle(x-3,y-2,2,col("#FF8CDB"));circle(x+3,y-2,2,col("#FF8CDB"));}
        }
        void drawPlayer(){
            long now=System.currentTimeMillis();float bob=(float)Math.sin(now/135.0)*1.7f;circle(px,py+15,19,Color.argb(115,0,0,0));
            glow(px,py,22,Color.argb(45,127,217,255),11);
            float faceX=(float)Math.cos(aimAngle),faceY=(float)Math.sin(aimAngle);
            // body and backpack, shadow stays under the feet
            circle(px,py+7,13,col("#22323B"));box(px-12,py-10+bob,px+12,py+13+bob,col("#344C5C"),7);
            box(px-9,py-9+bob,px-4,py+9+bob,col("#72909B"),2);box(px+4,py-9+bob,px+9,py+9+bob,col("#263944"),2);
            circle(px,py-7+bob,11,col("#C2A48A"));circle(px-3,py-8+bob,2,col("#45332D"));circle(px+4,py-8+bob,2,col("#45332D"));
            Path hood=new Path();hood.moveTo(px-11,py-10+bob);hood.quadTo(px,py-25+bob,px+11,py-10+bob);hood.lineTo(px+8,py-4+bob);hood.lineTo(px-8,py-4+bob);hood.close();paint(col("#202433"));c.drawPath(hood,p);
            // arm follows the aim direction, creating a readable top-down gun pose
            float gx=px+faceX*15,gy=py+faceY*15;line(px+faceX*3,py+faceY*3,gx,gy,col("#A78E80"),7);
            float endX=gx+faceX*21,endY=gy+faceY*21;line(gx,gy,endX,endY,col("#171A25"),6);line(gx,gy,endX,endY,col("#8296A4"),2);
            if(now<firePressedAt+95){glow(endX+faceX*7,endY+faceY*7,8,col("#FFD477"),10);circle(endX+faceX*7,endY+faceY*7,4,col("#FFF4C0"));}
            if(now<invincibleUntil){for(int k=0;k<2;k++){paint(Color.argb(110,113,225,255));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawCircle(px,py,22+k*4,p);p.setStyle(Paint.Style.FILL);}}
            // boots give the little survivor a clear direction silhouette
            box(px-11,py+7+bob,px-3,py+16+bob,col("#151923"),3);box(px+3,py+7+bob,px+11,py+16+bob,col("#151923"),3);
        }

        void drawHUD(long now){
            // compact survivor card
            box(12,10,226,77,Color.argb(220,12,17,25),11);stroke(12,10,226,77,Color.argb(160,119,160,180),11,1);
            text("FRONTIER SURVIVOR",24,28,9,col("#ADC2CA"),true);text("LV "+level+"   "+zoneName(currentZone()),24,45,11,Color.WHITE,true);box(198,15,220,37,soundOn?col("#263C43"):col("#44303A"),5);centre(soundOn?"♪":"×",209,30,11,soundOn?col("#A7F0D0"):col("#FF9CA5"),true);
            box(24,53,210,60,col("#30323A"),4);box(24,53,24+186*health/(float)maxHealth,60,health>35?col("#60D69B"):col("#FF657B"),4);text(health+"/"+maxHealth+" HP",24,72,9,Color.WHITE,true);
            // main expedition objective
            box(237,10,529,77,Color.argb(210,12,17,25),10);text("FIELD OBJECTIVE",249,28,9,col("#D3B4FF"),true);
            int z=currentZone(),n=relaysIn(z);String target;
            if(!regionUnlocked(z))target="THE REGION IS SEALED";
            else if(n<4)target="Activate signal relays  •  "+n+"/4";
            else if(!bossDefeated[z]&&zoneKills[z]<killGoal(z))target="Hunt monsters  •  "+zoneKills[z]+"/"+killGoal(z);
            else if(!bossDefeated[z])target=bossSpawned[z]?"HUNT THE GUARDIAN":"Approach the Guardian Altar";
            else target=z==5?"THE FRONTIER IS YOURS • Keep exploring":"Zone secured • Travel to the next region";
            text(target,249,47,11,Color.WHITE,true);text("KILLS "+kills+"   •   RELAYS "+relaysTotal+"/24   •   CACHES "+openedCaches+"/12",249,64,8,col("#AAB7C6"),true);
            // weapon selector
            box(539,10,788,77,Color.argb(220,12,17,25),10);
            for(int i=0;i<3;i++){float x=548+i*79;int width=73;int co=currentGun==i?col("#8D69DB"):col("#293340");box(x,17,x+width,68,co,8);if(!gunUnlocked[i]){centre("LOCKED",x+width/2,38,8,col("#8C97A8"),true);centre(new String[]{"PISTOL","SMG","SHOTGUN"}[i],x+width/2,53,8,col("#A6B4C0"),true);}else{centre(new String[]{"▰","≋","✣"}[i],x+width/2,35,14,col("#E8D39A"),true);centre(new String[]{"PISTOL","SMG","12G SHOT"}[i],x+width/2,49,8,Color.WHITE,true);centre(mag[i]+"/"+reserve[i],x+width/2,61,8,col("#C5DBDE"),true);}}
            drawMiniMap(806,10,142,86);
            // current interaction prompt floats near lower edge
            Landmark near=nearestLandmark(px,py,true);if(near!=null&&dist(px,py,near.x,near.y)<155){String t=poiPrompt(near);box(285,427,675,464,Color.argb(220,11,16,25),8);centre(t,480,443,10,Color.WHITE,true);centre("Press INTERACT near the marker",480,456,8,col("#9FB4C1"),false);}
            if(reloadUntil>now) {box(398,384,562,410,Color.argb(225,10,12,18),6);centre("RELOADING",480,402,10,col("#F3D58D"),true);}
            if(currentGun==0&&mag[0]<=0&&reserve[0]<=0)text("FIND AMMO",426,421,9,col("#FF7A89"),true);
        }
        String poiPrompt(Landmark q){
            if(q.type==0)return q.used?"RELAY STABILIZED":"INTERACT  •  "+q.name;
            if(q.type==1)return q.used?"CACHE EMPTY":"INTERACT  •  "+q.name;
            if(q.type==2)return bossDefeated[q.zone]?"GUARDIAN DEFEATED":relaysIn(q.zone)<4?"ALTAR SEALED  •  RELAYS "+relaysIn(q.zone)+"/4":!regionUnlocked(q.zone)?"SEALED BY THE FRONTIER":"INTERACT  •  SUMMON GUARDIAN";
            return "INTERACT  •  "+q.name+"  •  REST / SUPPLY";
        }
        void drawMiniMap(float x,float y,float w,float h){
            box(x,y,x+w,y+h,Color.argb(230,7,12,19),8);stroke(x,y,x+w,y+h,col("#6A7189"),8,1);
            float sc=Math.min((w-12)/WORLD_W,(h-12)/WORLD_H),mw=WORLD_W*sc,mh=WORLD_H*sc,ox=x+(w-mw)/2,oy=y+(h-mh)/2;
            for(int i=0;i<6;i++){int row=i<3?0:1,gc=row==0?i:5-i;box(ox+gc*ZONE*sc,oy+row*ZONE*sc,ox+(gc+1)*ZONE*sc,oy+(row+1)*ZONE*sc,Color.argb(bossDefeated[i]?230:130,Color.red(groundColor(i)),Color.green(groundColor(i)),Color.blue(groundColor(i))),0);}
            for(Landmark q:points)if(q.type==0&&!q.used)circle(ox+q.x*sc,oy+q.y*sc,1.6f,accent(q.zone));
            for(Mob m:mobs)if(!m.big&&m.alive&&dist(m.x,m.y,px,py)<800)circle(ox+m.x*sc,oy+m.y*sc,1.7f,col("#FF6875"));
            circle(ox+px*sc,oy+py*sc,3.3f,col("#FFFFFF"));circle(ox+px*sc,oy+py*sc,5,col("#8CDBFF"));
            if(mapOpen){text("WORLD MAP",x+5,y+h+13,8,col("#D7D0EE"),true);}
        }
        void drawControls(long now){
            // Left virtual movement stick
            circle(105,430,69,Color.argb(74,210,231,243));circle(105,430,56,Color.argb(62,15,23,35));paint(Color.argb(120,222,231,242));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawCircle(105,430,69,p);p.setStyle(Paint.Style.FILL);
            float mx=movePid>=0?moveTouchX:105,my=movePid>=0?moveTouchY:430;float dx=mx-105,dy=my-430,dl=(float)Math.hypot(dx,dy);if(dl>48){mx=105+dx*48/dl;my=430+dy*48/dl;}circle(mx,my,26,Color.argb(155,148,180,197));circle(mx-5,my-7,7,Color.argb(100,255,255,255));
            centre("MOVE",105,511,9,Color.argb(170,240,247,255),true);
            // Visible medkit button with a separate touch target.
            circle(55,340,29,Color.argb(180,49,132,93));stroke(26,311,84,369,col("#A2F0C8"),29,2);centre("+",55,347,23,Color.WHITE,true);centre("MED",55,378,8,col("#B8F7D5"),true);
            // Aim control
            circle(770,430,58,Color.argb(64,211,232,252));paint(Color.argb(115,210,231,245));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawCircle(770,430,58,p);p.setStyle(Paint.Style.FILL);
            float ax=aimPid>=0?aimTouchX:770,ay=aimPid>=0?aimTouchY:430,adx=ax-770,ady=ay-430,adl=(float)Math.hypot(adx,ady);if(adl>41){ax=770+adx*41/adl;ay=430+ady*41/adl;}circle(ax,ay,22,Color.argb(165,211,224,239));line(770,430,ax,ay,Color.argb(110,255,255,255),2);centre("AIM",770,511,9,Color.argb(175,240,247,255),true);
            // Fire / dodge / reload / interact
            circle(875,337,48,Color.argb(firing?235:175,139,54,65));paint(Color.argb(220,255,178,153));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawCircle(875,337,48,p);p.setStyle(Paint.Style.FILL);circle(875,337,34,col("#7E333F"));centre("FIRE",875,334,13,Color.WHITE,true);centre("HOLD",875,349,7,col("#FFE0D5"),true);
            circle(674,340,33,Color.argb(now<dashReadyAt?85:170,61,133,183));stroke(641,307,707,373,now<dashReadyAt?col("#516B7A"):col("#9DE9FF"),33,2);centre(now<dashReadyAt?"…":"DASH",674,339,10,Color.WHITE,true);centre(now<dashReadyAt?Math.max(0,(dashReadyAt-now)/1000f)+"s":"ROLL",674,352,7,col("#CBEFFF"),true);
            circle(859,447,27,Color.argb(170,171,137,74));stroke(832,420,886,474,col("#EAD59E"),27,2);centre("R",859,451,15,Color.WHITE,true);
            circle(925,447,27,Color.argb(180,59,143,107));stroke(898,420,952,474,col("#A3F0C9"),27,2);centre("USE",925,450,10,Color.WHITE,true);
            box(367,482,593,523,Color.argb(190,12,18,26),7);centre("HP "+health+"/"+maxHealth+"    MED "+medkits+"    "+new String[]{"PISTOL","SMG","SHOTGUN"}[currentGun],480,498,9,Color.WHITE,true);centre("TAP R TO RELOAD  •  TAP USE AT RELAYS / CHESTS",480,512,7.4f,col("#B7C9D6"),true);
        }
        void drawNotice(long now){
            float alpha=Math.min(1,(noticeUntil-now)/300f);box(267,89,693,127,Color.argb((int)(220*alpha),11,16,25),8);stroke(267,89,693,127,Color.argb((int)(150*alpha),129,103,178),8,1);centre(notice,480,112,12,Color.WHITE,true);
        }
        void drawFullMap(){
            box(0,0,W,H,Color.argb(235,4,7,12),0);
            centre("THE SHATTERED FRONTIER",480,40,20,Color.WHITE,true);centre("Explore, stabilise relays, open supply caches and defeat each regional guardian.",480,59,10,col("#B4C3D1"),false);
            float sc=0.046f,ww=WORLD_W*sc,hh=WORLD_H*sc,ox=(W-ww)/2,oy=83;
            for(int i=0;i<6;i++){int row=i<3?0:1,gc=row==0?i:5-i;float l=ox+gc*ZONE*sc,t=oy+row*ZONE*sc;box(l,t,l+ZONE*sc,t+ZONE*sc,groundColor(i),0);stroke(l,t,l+ZONE*sc,t+ZONE*sc,accent(i),0,2);centre(zoneName(i),l+ZONE*sc/2,t+22,10,Color.WHITE,true);}
            for(Landmark q:points){int co=q.type==0?(q.used?col("#5D6878"):col("#7FE6BA")):q.type==1?(q.used?col("#5D6878"):col("#F1CD75")):q.type==2?(bossDefeated[q.zone]?col("#687780"):col("#FF698C")):col("#F1A460");circle(ox+q.x*sc,oy+q.y*sc,q.type==2?5:3,co);}
            for(Mob m:mobs)if(m.alive)circle(ox+m.x*sc,oy+m.y*sc,m.big?5:2,col("#FF667E"));
            circle(ox+px*sc,oy+py*sc,7,Color.WHITE);circle(ox+px*sc,oy+py*sc,12,col("#8ADFFF"));
            box(270,493,690,526,Color.argb(220,22,30,42),7);centre("Tap anywhere to close map  •  White marker = you  •  Green = relay  •  Gold = cache  •  Pink = guardian",480,509,9,Color.WHITE,true);
        }

        void updateGame(float dt,long now){
            if(movePid>=0){float dx=moveTouchX-105,dy=moveTouchY-430,dl=(float)Math.hypot(dx,dy);if(dl>12){moveX=dx/Math.max(48,dl);moveY=dy/Math.max(48,dl);}else{moveX=0;moveY=0;}}else{moveX=0;moveY=0;}
            if(aimPid>=0){float dx=aimTouchX-770,dy=aimTouchY-430,dl=(float)Math.hypot(dx,dy);if(dl>10){aimX=dx/Math.max(41,dl);aimY=dy/Math.max(41,dl);aimAngle=(float)Math.atan2(aimY,aimX);aimActive=true;}}else{aimActive=false;aimX=0;aimY=0;}
            boolean dashing=now<dashUntil;float speed=dashing?565:210;
            movePlayer(moveX*speed*dt,moveY*speed*dt);
            if(reloadUntil>0&&reloadUntil<=now){finishReload();reloadUntil=0;}
            if(firing&&reloadUntil<=now)fireGun(now);
            updateMobs(dt,now);updateShots(dt,now);updateDrops(now);updateSparks(dt);
            if(mobs.size()<9&&now-lastSpawn>2800&&now>dashUntil&&!guardianAlive()){spawnMobAroundPlayer(false);lastSpawn=now;}
            if(now-lastSave>2400){save();lastSave=now;}
            // Periodic bounty keeps exploration rewarding beyond the primary objectives.
            if(kills>0&&kills%25==0&&playerDamage<kills){playerDamage=kills;coins+=120;medkits++;notification("BOUNTY COMPLETE  •  +120 CREDITS  •  +1 MEDKIT",3000);play(sQuest);save();}
            Landmark q=activeObjective(currentZone());if(q!=null){float d=dist(px,py,q.x,q.y);objectiveText=q.name+"  •  "+(int)d+"m";}else objectiveText="The frontier is secured. Explore for supplies.";
        }
        void movePlayer(float dx,float dy){
            if(dx==0&&dy==0)return;float nx=Math.max(28,Math.min(WORLD_W-28,px+dx)),ny=Math.max(28,Math.min(WORLD_H-28,py+dy));
            int oldZone=zoneAt(px,py),newZone=zoneAt(nx,ny);
            if(newZone!=oldZone&&!regionUnlocked(newZone)){notification("SIGNAL SEAL BLOCKS THIS ROUTE",850);return;}
            if(!collides(nx,py))px=nx;if(!collides(px,ny))py=ny;
        }
        boolean collides(float x,float y){
            for(Prop q:props){if(!q.solid||Math.abs(q.x-x)>q.r+20||Math.abs(q.y-y)>q.r+20)continue;if(dist(x,y,q.x,q.y)<q.r+15)return true;}
            for(Landmark q:points)if(q.type==3&&dist(x,y,q.x,q.y)<24)return true;
            return false;
        }
        Landmark activeObjective(int z){
            if(!regionUnlocked(z))return null;
            for(Landmark q:points)if(q.zone==z&&q.type==0&&!q.used)return q;
            if(!bossDefeated[z])for(Landmark q:points)if(q.zone==z&&q.type==2&&(!q.used||bossSpawned[z]))return q;
            if(z<5&&bossDefeated[z])return null;return null;
        }
        void spawnMobAroundPlayer(boolean big){
            int z=currentZone();if(!regionUnlocked(z))z=0;
            float x=px,y=py;boolean ok=false;
            for(int tries=0;tries<28;tries++){double a=rng.nextDouble()*Math.PI*2;float rad=440+rng.nextFloat()*310;x=px+(float)Math.cos(a)*rad;y=py+(float)Math.sin(a)*rad;if(x<60||y<60||x>WORLD_W-60||y>WORLD_H-60)continue;if(zoneAt(x,y)!=z||collides(x,y))continue;ok=true;break;}
            if(!ok){x=Math.max(100,Math.min(WORLD_W-100,px+360));y=Math.max(100,Math.min(WORLD_H-100,py+290));}
            int type=rng.nextInt(7);Mob m=new Mob(x,y,type,z,big);mobs.add(m);
        }
        void spawnGuardian(int z){
            for(Mob m:mobs)if(m.big&&m.zone==z&&m.alive)return;
            Landmark altar=null;for(Landmark q:points)if(q.zone==z&&q.type==2){altar=q;break;}
            float x=altar==null?px+90:altar.x+80,y=altar==null?py:altar.y+30;
            mobs.add(new Mob(x,y,z,z,true));notification("GUARDIAN AWAKENED  •  "+zoneName(z),3500);play(sBoss);
        }
        void updateMobs(float dt,long now){
            for(int i=mobs.size()-1;i>=0;i--){Mob m=mobs.get(i);if(!m.alive){mobs.remove(i);continue;}
                float dx=px-m.x,dy=py-m.y,d=(float)Math.hypot(dx,dy);if(!m.big&&d>1180){mobs.remove(i);continue;}
                if(now<m.stunUntil)continue;
                m.angle=(float)Math.atan2(dy,dx);
                if(m.type==2&&!m.big&&d<350&&d>160){
                    m.x-=dx/(d+0.01f)*speedSafe()*dt;m.y-=dy/(d+0.01f)*speedSafe()*dt;
                    if(now>m.nextAttack){m.nextAttack=now+1750;shootEnemy(m,7+m.zone*2,260,0);play(sHit);}
                }else if(m.type==5&&!m.big&&d<250&&d>95){
                    float vx=-dy/(d+0.01f),vy=dx/(d+0.01f);m.x+=vx*75*dt;m.y+=vy*75*dt;if(now>m.nextAttack){m.nextAttack=now+1450;shootEnemy(m,8+m.zone*2,300,0);}
                }else if(d>(m.big?61:28)){
                    float speed=(m.big?62:m.type==1?145:m.type==4?110:m.type==6?82:78)*(1+m.zone*0.055f);
                    if(!collides(m.x+dx/(d+0.01f)*speed*dt,m.y))m.x+=dx/(d+0.01f)*speed*dt;
                    if(!collides(m.x,m.y+dy/(d+0.01f)*speed*dt))m.y+=dy/(d+0.01f)*speed*dt;
                }else if(now>m.nextAttack&&now>invincibleUntil){
                    m.nextAttack=now+(m.big?720:m.type==1?520:900);int hurt=m.damage;
                    if(now<dashUntil){notification("DODGE!",500);}else{health-=hurt;lastDamageAt=now;damageFlashUntil=now+160;invincibleUntil=now+450;notification(m.big?"GUARDIAN STRIKE  -"+hurt:"HIT  -"+hurt,650);play(sHit);if(health<=0)playerDown();}
                }
                if(m.big&&d<450&&now>m.nextAttack&&now>invincibleUntil){m.nextAttack=now+1400;shootEnemy(m,12+m.zone*3,265,-0.22f);shootEnemy(m,12+m.zone*3,265,0);shootEnemy(m,12+m.zone*3,265,0.22f);}
            }
        }
        float speedSafe(){return 40;}

        void shootEnemy(Mob m,int damage,float speed,float spread){float a=m.angle+spread;shots.add(new Shot(m.x+(float)Math.cos(a)*18,m.y+(float)Math.sin(a)*18,(float)Math.cos(a)*speed,(float)Math.sin(a)*speed,damage,true,m.type));}
        void updateShots(float dt,long now){
            for(int i=shots.size()-1;i>=0;i--){Shot b=shots.get(i);b.x+=b.vx*dt;b.y+=b.vy*dt;b.life--;
                if(b.life<=0||b.x<0||b.y<0||b.x>WORLD_W||b.y>WORLD_H){shots.remove(i);continue;}
                if(b.hostile){if(dist(b.x,b.y,px,py)<19){shots.remove(i);if(now>=invincibleUntil&&now>=dashUntil){health-=b.damage;invincibleUntil=now+490;damageFlashUntil=now+150;play(sHit);notification("PROJECTILE IMPACT  -"+b.damage,600);if(health<=0)playerDown();}continue;}}
                else{
                    boolean hit=false;
                    for(Mob m:mobs){if(!m.alive)continue;float rad=m.big?44:23;if(dist(b.x,b.y,m.x,m.y)<rad){m.hp-=b.damage;hit=true;shotsHit++;for(int k=0;k<5;k++)particle(b.x,b.y,m.big?col("#FF6F98"):col("#F7C56C"),2.3f,13);m.stunUntil=now+(m.big?50:100);
                        if(m.hp<=0)killMob(m);break;}}
                    if(hit){shots.remove(i);continue;}
                }
            }
        }
        void fireGun(long now){
            if(now-lastShot<new int[]{270,105,590}[currentGun])return;
            if(mag[currentGun]<=0){if(reserve[currentGun]>0){startReload(now);return;}notification("OUT OF AMMO  •  FIND A SUPPLY CACHE",850);play(sEmpty);firing=false;return;}
            float a=aimAngle;
            if(!aimActive){Mob target=nearestMob(790);if(target!=null)a=(float)Math.atan2(target.y-py,target.x-px);}
            int gun=currentGun, count=gun==2?5:1;float spread=gun==0?0.035f:gun==1?0.095f:0.52f;
            for(int i=0;i<count;i++){float ang=a+(count==1?(rng.nextFloat()*2-1)*spread:(i-(count-1)/2f)*spread+(rng.nextFloat()-.5f)*0.09f);float speed=gun==0?710:gun==1?760:640;int damage=(gun==0?25:gun==1?12:17)+level/3;
                shots.add(new Shot(px+(float)Math.cos(ang)*27,py+(float)Math.sin(ang)*27,(float)Math.cos(ang)*speed,(float)Math.sin(ang)*speed,damage,false,gun));}
            mag[gun]--;shotsFired++;lastShot=now;firePressedAt=now;
            play(gun==0?sPistol:gun==1?sRifle:sShotgun);
            if(gun==2){for(int i=0;i<11;i++)particle(px+(float)Math.cos(a)*31,py+(float)Math.sin(a)*31,col("#FFC974"),3.4f,8);}
            if(mag[gun]==0&&reserve[gun]>0)startReload(now);
        }
        Mob nearestMob(float maxDistance){Mob best=null;float bd=maxDistance;for(Mob m:mobs){if(!m.alive)continue;float d=dist(px,py,m.x,m.y);if(d<bd){bd=d;best=m;}}return best;}
        void startReload(long now){if(reloadUntil>now||mag[currentGun]>=new int[]{12,32,6}[currentGun]||reserve[currentGun]<=0)return;reloadUntil=now+(currentGun==0?900:currentGun==1?1250:1500);play(sReload);}
        void finishReload(){int cap=new int[]{12,32,6}[currentGun],need=cap-mag[currentGun],take=Math.min(need,reserve[currentGun]);mag[currentGun]+=take;reserve[currentGun]-=take;play(sReload);}
        void switchGun(int n){if(n<0||n>2)return;if(!gunUnlocked[n]){notification("LOCKED  •  FIND A WEAPON CACHE",1000);play(sEmpty);return;}currentGun=n;reloadUntil=0;play(sReload);}
        void killMob(Mob m){
            if(!m.alive)return;m.alive=false;kills++;zoneKills[m.zone]++;coins+=m.big?380+80*m.zone:4+2*m.type+2*m.zone;addXP(m.big?600+level*13:18+level*3+m.zone*5);
            if(m.big){bossDefeated[m.zone]=true;bossSpawned[m.zone]=false;coins+=600;medkits++;notification("GUARDIAN SLAIN  •  REGION "+(m.zone+1)+" UNSEALED",3600);play(sQuest);for(int i=0;i<38;i++)particle(m.x,m.y,accent(m.zone),4.5f,32);}
            else{for(int i=0;i<10;i++)particle(m.x,m.y,m.type==2?col("#9BE9FF"):col("#F5A86D"),2.5f,18);if(rng.nextInt(100)<28)drops.add(new Drop(m.x+12,m.y,0,6+rng.nextInt(15)+m.zone*3,m.zone));if(rng.nextInt(100)<15)drops.add(new Drop(m.x-9,m.y+8,1,0,m.zone));if(rng.nextInt(100)<10)drops.add(new Drop(m.x+7,m.y-6,2,0,m.zone));if(rng.nextInt(100)<12)drops.add(new Drop(m.x+17,m.y-5,3,0,m.zone));if(rng.nextInt(100)<4)drops.add(new Drop(m.x,m.y,4,rng.nextInt(2)+1,m.zone));}
            save();
        }
        void playerDown(){health=Math.max(1,maxHealth/2);coins=Math.max(0,coins-45);px=checkpointX;py=checkpointY;invincibleUntil=System.currentTimeMillis()+2500;shots.removeIf(b->b.hostile);notification("DOWNED  •  RESPAWNED AT LAST CAMP  •  -45 CREDITS",3400);play(sBoss);save();}
        void updateDrops(long now){
            for(int i=drops.size()-1;i>=0;i--){Drop d=drops.get(i);if(dist(px,py,d.x,d.y)<34){
                if(d.type==0){coins+=d.value;notification("+"+d.value+" CREDITS",500);}
                else if(d.type==1){for(int j=0;j<3;j++)reserve[j]+=j==0?12:j==1?28:8;ammoPickupCount++;notification("AMMO RESTOCKED",750);play(sLoot);}
                else if(d.type==2){medkits++;notification("MEDKIT FOUND",900);play(sLoot);}
                else if(d.type==3){addXP(25+d.zone*4);notification("ANCIENT SHARD  •  XP",700);play(sLoot);}
                else{int gun=Math.max(0,Math.min(2,d.value));gunUnlocked[gun]=true;reserve[gun]+=gun==1?72:gun==2?18:24;notification(new String[]{"PISTOL","SMG","SHOTGUN"}[gun]+" DISCOVERED",1900);play(sQuest);}
                drops.remove(i);save();
            }else if(now-d.born>90000)drops.remove(i);}
        }
        void updateSparks(float dt){for(int i=sparks.size()-1;i>=0;i--){Spark s=sparks.get(i);s.x+=s.vx*dt;s.y+=s.vy*dt;s.vx*=0.92f;s.vy*=0.92f;s.life--;if(s.life<=0)sparks.remove(i);}}
        void drawSparks(){for(Spark s:sparks)if(visible(s.x,s.y,5))circle(s.x,s.y,s.size,s.color);}
        void particle(float x,float y,int color,float speed,int life){double a=rng.nextDouble()*Math.PI*2;float v=rng.nextFloat()*speed;sparks.add(new Spark(x,y,(float)Math.cos(a)*v,(float)Math.sin(a)*v,1.2f+rng.nextFloat()*2.5f,color,life));if(sparks.size()>260)sparks.remove(0);}

        void interact(){
            Landmark q=nearestLandmark(px,py,true);long now=System.currentTimeMillis();
            if(q==null||dist(px,py,q.x,q.y)>125){notification("MOVE CLOSER TO A RELAY OR CACHE",950);play(sEmpty);return;}
            if(q.type==0){
                if(!regionUnlocked(q.zone)){notification("REGION SEALED",950);return;}
                q.used=true;relayCount[q.zone]++;relaysTotal++;coins+=65+q.zone*10;addXP(90+q.zone*20);reserve[currentGun]+=currentGun==0?7:currentGun==1?18:5;
                health=Math.min(maxHealth,health+14);notification("SIGNAL RELAY STABLE  •  "+relayCount[q.zone]+"/4  •  +65 CREDITS",2100);play(sQuest);for(int i=0;i<18;i++)particle(q.x,q.y,accent(q.zone),3,25);
                if(relayCount[q.zone]>=4&&!bossDefeated[q.zone])notification("ALL SIGNALS STABLE  •  RETURN TO THE GUARDIAN ALTAR",3200);save();
            }else if(q.type==1){
                if(q.used){notification("THIS CACHE HAS ALREADY BEEN OPENED",850);return;}
                q.used=true;openedCaches++;coins+=180+q.zone*40;addXP(110+q.zone*24);for(int j=0;j<3;j++)reserve[j]+=j==0?24:j==1?60:12;medkits++;health=Math.min(maxHealth,health+30);
                if(rng.nextInt(100)<48){int next=!gunUnlocked[1]?1:!gunUnlocked[2]?2:rng.nextBoolean()?1:2;gunUnlocked[next]=true;reserve[next]+=next==1?72:18;drops.add(new Drop(q.x,q.y-35,4,next,q.zone));}
                notification("SUPPLY CACHE OPENED  •  CREDITS, AMMO & MEDKIT",2300);play(sLoot);for(int i=0;i<22;i++)particle(q.x,q.y,col("#EBCB79"),3.5f,26);save();
            }else if(q.type==2){
                if(bossDefeated[q.zone]){notification("THIS GUARDIAN HAS FALLEN",900);return;}
                if(!regionUnlocked(q.zone)||relaysIn(q.zone)<4){notification("ALTAR SEALED  •  STABILISE "+(4-relaysIn(q.zone))+" MORE RELAYS",1500);play(sEmpty);return;}
                if(zoneKills[q.zone]<killGoal(q.zone)){notification("ALTAR SEALED  •  HUNT "+(killGoal(q.zone)-zoneKills[q.zone])+" MORE MONSTERS",1600);play(sEmpty);return;}
                if(bossSpawned[q.zone]){notification("THE GUARDIAN IS ALREADY HUNTING YOU",1100);return;}
                q.used=true;bossSpawned[q.zone]=true;spawnGuardian(q.zone);save();
            }else{
                checkpointX=q.x;checkpointY=q.y;health=maxHealth;medkits=Math.max(medkits,1);for(int j=0;j<3;j++)reserve[j]+=j==0?18:j==1?36:10;
                notification("CAMP SUPPLIES RESTORED  •  CHECKPOINT SET",1800);play(sLoot);save();
            }
        }
        void useMedkit(){
            if(medkits<=0){notification("NO MEDKITS  •  SEARCH SUPPLY CACHES",900);play(sEmpty);return;}
            if(health>=maxHealth){notification("HEALTH IS ALREADY FULL",700);return;}
            medkits--;health=Math.min(maxHealth,health+52);play(sLoot);notification("MEDKIT USED  •  +52 HP",850);save();
        }
        void triggerDash(){
            long now=System.currentTimeMillis();if(now<dashReadyAt){notification("DASH RECHARGING",500);return;}
            if(Math.hypot(moveX,moveY)<0.15){moveX=(float)Math.cos(aimAngle);moveY=(float)Math.sin(aimAngle);}
            dashUntil=now+260;dashReadyAt=now+1550;invincibleUntil=now+330;for(int i=0;i<22;i++)particle(px,py,col("#88DCFF"),4,19);play(sDash);
        }
        void handleButton(float x,float y){
            if(y<84){
                if(x>812){mapOpen=!mapOpen;return;}
                if(x>=545&&x<787){int slot=(int)((x-548)/79);switchGun(Math.min(2,Math.max(0,slot)));return;}
            }
            if(mapOpen){mapOpen=false;return;}
            if(x<230&&y>345){movePid=0;moveTouchX=x;moveTouchY=y;return;}
            if(dist(x,y,875,337)<53){firing=true;firePid=0;firePressedAt=System.currentTimeMillis();return;}
            if(dist(x,y,674,340)<39){triggerDash();return;}
            if(dist(x,y,859,447)<32){startReload(System.currentTimeMillis());return;}
            if(dist(x,y,925,447)<34){interact();return;}
            if(x>=710&&x<=836&&y>=364){aimPid=0;aimTouchX=x;aimTouchY=y;return;}
            if(y>400&&x<250){movePid=0;moveTouchX=x;moveTouchY=y;}
        }
        void pointerDown(MotionEvent e,int index){
            int id=e.getPointerId(index);float x=e.getX(index)/sx,y=e.getY(index)/sy;
            if(y<42&&x>=195&&x<=230){toggleSound();return;}
            if(y<84&&x>812){mapOpen=!mapOpen;if(mapOpen)firing=false;return;}
            if(mapOpen){mapOpen=false;return;}
            if(y<84&&x>=545&&x<787){int slot=(int)((x-548)/79);switchGun(Math.min(2,Math.max(0,slot)));return;}
            if(x<230&&y>345&&movePid<0){movePid=id;moveTouchX=x;moveTouchY=y;return;}
            if(dist(x,y,875,337)<53&&firePid<0){firePid=id;firing=true;firePressedAt=System.currentTimeMillis();return;}
            if(dist(x,y,674,340)<39){triggerDash();return;}
            if(dist(x,y,859,447)<32){startReload(System.currentTimeMillis());return;}
            if(dist(x,y,925,447)<34){interact();return;}
            if(x>=710&&x<=836&&y>=364&&aimPid<0){aimPid=id;aimTouchX=x;aimTouchY=y;return;}
            if(dist(x,y,55,340)<35){useMedkit();return;}
        }
        void pointerMove(MotionEvent e){for(int i=0;i<e.getPointerCount();i++){int id=e.getPointerId(i);float x=e.getX(i)/sx,y=e.getY(i)/sy;if(id==movePid){moveTouchX=x;moveTouchY=y;}if(id==aimPid){aimTouchX=x;aimTouchY=y;}}}
        void pointerUp(MotionEvent e,int index){int id=e.getPointerId(index);if(id==movePid){movePid=-1;moveX=0;moveY=0;}if(id==aimPid){aimPid=-1;aimActive=false;}if(id==firePid){firePid=-1;firing=false;}}
        @Override public boolean onTouchEvent(MotionEvent e){
            int a=e.getActionMasked();
            if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_POINTER_DOWN){pointerDown(e,e.getActionIndex());invalidate();return true;}
            if(a==MotionEvent.ACTION_MOVE){pointerMove(e);invalidate();return true;}
            if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_POINTER_UP){pointerUp(e,e.getActionIndex());if(a==MotionEvent.ACTION_UP){movePid=-1;aimPid=-1;firePid=-1;firing=false;}invalidate();return true;}
            if(a==MotionEvent.ACTION_CANCEL){movePid=aimPid=firePid=-1;moveX=moveY=0;firing=false;aimActive=false;return true;}
            return true;
        }
    }
}