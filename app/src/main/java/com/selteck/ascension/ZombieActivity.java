package com.selteck.ascension;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * DEAD DISTRICT: a procedural third-person 3D survival shooter.
 * The city geometry is generated locally, so no network or asset download is required.
 */
public class ZombieActivity extends Activity {
    private GameSession session;
    private GLSurfaceView surface;
    private HudView hud;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.rgb(9, 14, 18));
        getWindow().setNavigationBarColor(Color.rgb(9, 14, 18));
        immersive();
        session = new GameSession();
        surface = new GLSurfaceView(this);
        surface.setEGLContextClientVersion(2);
        surface.setRenderer(new CityRenderer(session));
        surface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        hud = new HudView();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(13, 18, 20));
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));
        root.addView(hud, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) immersive();
    }
    @Override protected void onPause() {
        if (session != null) session.save();
        if (surface != null) surface.onPause();
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (surface != null) surface.onResume();
    }

    private final class HudView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final java.util.HashMap<Integer, Integer> roles = new java.util.HashMap<>();
        private float width, height, density;
        private int lastPointers = 0;
        private static final int MOVE = 1, AIM = 2, FIRE = 3;

        HudView() {
            super(ZombieActivity.this);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            setWillNotDraw(false);
            density = getResources().getDisplayMetrics().density;
        }
        private float dp(float v) { return v * density; }
        private void fill(Canvas c, int color) {
            p.setStyle(Paint.Style.FILL); p.setColor(color); p.setAlpha(Color.alpha(color));
        }
        private void rounded(Canvas c, float l, float t, float r, float b, float rad, int color) {
            fill(c, color); c.drawRoundRect(l,t,r,b,rad,rad,p);
        }
        private void label(Canvas c, String s, float x, float y, float size, int color, boolean bold) {
            fill(c, color); p.setTextSize(size); p.setTypeface(bold ? android.graphics.Typeface.create("sans-serif", 1) : android.graphics.Typeface.create("sans-serif", 0));
            p.setShadowLayer(dp(2),0,dp(1),Color.argb(160,0,0,0)); c.drawText(s,x,y,p); p.clearShadowLayer();
        }
        private void circle(Canvas c, float x, float y, float r, int color, int stroke) {
            fill(c,color); c.drawCircle(x,y,r,p);
            if (stroke != 0) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setColor(stroke); c.drawCircle(x,y,r,p); p.setStyle(Paint.Style.FILL); }
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            width=getWidth(); height=getHeight();
            synchronized (session) {
                float d=dp(1);
                rounded(c,dp(14),dp(12),dp(330),dp(76),dp(12),Color.argb(205,8,16,19));
                label(c,"DEAD DISTRICT",dp(27),dp(31),dp(15),Color.rgb(228,239,232),true);
                label(c,session.cityName(session.cityAt(session.px,session.pz)).toUpperCase(),dp(27),dp(49),dp(10),Color.rgb(126,213,164),true);
                label(c,"УРОВЕНЬ "+session.level+"  •  XP "+session.xp+"/"+session.nextLevelXp(),dp(27),dp(65),dp(10),Color.rgb(208,217,211),false);
                float bx=dp(350), by=dp(22), bw=dp(160);
                rounded(c,bx,by,bx+bw,by+dp(10),dp(6),Color.argb(150,0,0,0));
                rounded(c,bx,by,bx+bw*Math.max(0,session.hp)/(float)Math.max(1,session.maxHp),by+dp(10),dp(6),Color.rgb(194,66,62));
                label(c,"ЗДОРОВЬЕ "+session.hp+"/"+session.maxHp,bx,by+dp(28),dp(10),Color.WHITE,true);
                rounded(c,bx,by+dp(34),bx+bw,by+dp(41),dp(5),Color.argb(150,0,0,0));
                rounded(c,bx,by+dp(34),bx+bw*Math.max(0,session.armor)/100f,by+dp(41),dp(5),Color.rgb(81,157,202));
                label(c,"БРОНЯ "+session.armor+"   УБИЙСТВА "+session.kills,bx,by+dp(57),dp(10),Color.rgb(220,226,221),true);
                float right=width-dp(18);
                rounded(c,right-dp(226),dp(12),right,dp(64),dp(12),Color.argb(205,8,16,19));
                label(c,session.weaponName(session.weapon),right-dp(212),dp(32),dp(13),Color.rgb(251,210,116),true);
                label(c,""+session.mag[session.weapon]+" / "+session.reserve[session.weapon]+"   •   ⭐ "+session.cash,right-dp(212),dp(50),dp(11),Color.WHITE,false);
                String objective=session.objectiveText();
                label(c,objective,Math.max(dp(18),width*0.34f),dp(92),dp(11),Color.rgb(233,236,228),true);

                // Crosshair and damage vignette.
                float cx=width*0.5f, cy=height*0.48f, cr=dp(7);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.6f)); p.setColor(Color.argb(190,242,243,220));
                c.drawCircle(cx,cy,cr,p); c.drawLine(cx-dp(12),cy,cx-dp(4),cy,p); c.drawLine(cx+dp(4),cy,cx+dp(12),cy,p); c.drawLine(cx,cy-dp(12),cx,cy-dp(4),p); c.drawLine(cx,cy+dp(4),cx,cy+dp(12),p); p.setStyle(Paint.Style.FILL);
                if (session.damageFlashUntil > System.currentTimeMillis()) {
                    fill(c,Color.argb(42,210,28,20)); c.drawRect(0,0,width,height,p);
                }

                // Virtual sticks.
                float stickR=dp(54), leftX=dp(92), stickY=height-dp(92), rightX=width-dp(232);
                circle(c,leftX,stickY,stickR,Color.argb(65,222,239,229),Color.argb(115,224,240,226));
                circle(c,leftX+session.moveX*dp(28),stickY-session.moveY*dp(28),dp(22),Color.argb(155,160,204,179),Color.argb(230,232,250,231));
                circle(c,rightX,stickY,stickR,Color.argb(55,235,224,191),Color.argb(115,233,220,181));
                if(session.aimActive) circle(c,rightX+session.aimX*dp(28),stickY-session.aimY*dp(28),dp(22),Color.argb(160,245,193,103),Color.argb(235,250,226,179));
                else circle(c,rightX,stickY,dp(11),Color.argb(100,246,224,166),0);

                // Action controls.
                float fireX=width-dp(79), fireY=height-dp(99);
                button(c,fireX,fireY,dp(42),"FIRE",session.firing,Color.rgb(185,62,48));
                button(c,width-dp(76),height-dp(194),dp(29),session.inside?"EXIT":"USE",false,Color.rgb(65,139,97));
                button(c,width-dp(148),height-dp(194),dp(27),"MED",false,Color.rgb(74,125,172));
                button(c,width-dp(148),height-dp(99),dp(27),"R",false,Color.rgb(94,103,107));
                button(c,width-dp(76),height-dp(285),dp(27),"GUN",false,Color.rgb(132,99,54));
                button(c,width-dp(148),height-dp(285),dp(27),"UP",false,Color.rgb(91,111,153));
                button(c,width-dp(224),height-dp(180),dp(24),"RUN",false,Color.rgb(77,116,98));
                label(c,"⟵ ДВИЖЕНИЕ",dp(38),height-dp(22),dp(8),Color.argb(210,238,239,223),false);
                label(c,"ПОВОРОТ / ПРИЦЕЛ",width-dp(294),height-dp(22),dp(8),Color.argb(210,238,239,223),false);
                if (session.noticeUntil > System.currentTimeMillis() && session.notice.length()>0) {
                    float nWidth=Math.min(width-dp(80),dp(490));
                    rounded(c,(width-nWidth)/2,height*0.68f-dp(17),(width+nWidth)/2,height*0.68f+dp(17),dp(8),Color.argb(218,8,15,18));
                    p.setTextSize(dp(12)); p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    label(c,session.notice,width/2-p.measureText(session.notice)/2,height*0.68f+dp(4),dp(12),Color.rgb(242,238,218),true);
                }
                if (session.skillPoints > 0) label(c,"ОЧКИ НАВЫКОВ: "+session.skillPoints, width-dp(240),dp(84),dp(10),Color.rgb(255,218,112),true);
                if (session.inside) label(c,"ИНТЕРЬЕР • ОБЫСКАЙ КОМНАТЫ",width*0.40f,dp(62),dp(10),Color.rgb(241,212,153),true);
            }
            postInvalidateDelayed(40);
        }
        private void button(Canvas c,float x,float y,float r,String s,boolean active,int color) {
            circle(c,x,y,r,Color.argb(active?235:178,Color.red(color),Color.green(color),Color.blue(color)),Color.argb(230,232,238,224));
            fill(c,Color.WHITE); p.setTextSize(dp(s.length()>4?8:10)); p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            c.drawText(s,x-p.measureText(s)/2,y+dp(3),p);
        }
        private int roleAt(float x,float y) {
            float h=height,w=width,d=dp(1);
            if (distance(x,y,w-dp(79),h-dp(99)) < dp(48)) return FIRE;
            if (distance(x,y,w-dp(76),h-dp(194)) < dp(34)) { session.interact(); return 10; }
            if (distance(x,y,w-dp(148),h-dp(194)) < dp(31)) { session.useMedkit(); return 11; }
            if (distance(x,y,w-dp(148),h-dp(99)) < dp(31)) { session.reload(); return 12; }
            if (distance(x,y,w-dp(76),h-dp(285)) < dp(31)) { session.cycleWeapon(); return 13; }
            if (distance(x,y,w-dp(148),h-dp(285)) < dp(31)) { session.upgrade(); return 14; }
            if (distance(x,y,w-dp(224),h-dp(180)) < dp(29)) { session.sprinting=true; return 15; }
            if (x < width*0.40f) return MOVE;
            if (x > width*0.48f && y > height*0.42f) return AIM;
            return 0;
        }
        private float distance(float x,float y,float xx,float yy) { float dx=x-xx,dy=y-yy; return (float)Math.sqrt(dx*dx+dy*dy); }
        @Override public boolean onTouchEvent(MotionEvent ev) {
            int action=ev.getActionMasked(), index=ev.getActionIndex(), id=ev.getPointerId(index);
            float x=ev.getX(index), y=ev.getY(index);
            synchronized(session) {
                if(action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) {
                    int role=roleAt(x,y); roles.put(id,role);
                    if(role==MOVE) updateStick(x,y,true);
                    else if(role==AIM) updateAim(x,y,true);
                    else if(role==FIRE) session.firing=true;
                    return true;
                }
                if(action==MotionEvent.ACTION_MOVE) {
                    for(int i=0;i<ev.getPointerCount();i++) {
                        int pid=ev.getPointerId(i); Integer role=roles.get(pid);
                        if(role==null) continue;
                        if(role==MOVE) updateStick(ev.getX(i),ev.getY(i),true);
                        else if(role==AIM) updateAim(ev.getX(i),ev.getY(i),true);
                    }
                    return true;
                }
                if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP || action==MotionEvent.ACTION_CANCEL) {
                    Integer role=roles.remove(id);
                    if(role!=null) {
                        if(role==MOVE) {session.moveX=0;session.moveY=0;}
                        if(role==AIM) {session.aimX=0;session.aimY=0;session.aimActive=false;}
                        if(role==FIRE) session.firing=false;
                        if(role!=null && role==15) session.sprinting=false;
                    }
                    if(action==MotionEvent.ACTION_CANCEL) {roles.clear();session.moveX=0;session.moveY=0;session.aimX=0;session.aimY=0;session.aimActive=false;session.firing=false;session.sprinting=false;}
                    return true;
                }
            }
            return true;
        }
        private void updateStick(float x,float y,boolean active) {
            float cx=dp(92), cy=height-dp(92), r=dp(54), dx=x-cx,dy=cy-y, dist=(float)Math.sqrt(dx*dx+dy*dy);
            if(dist>r) {dx*=r/dist;dy*=r/dist;}
            session.moveX=clamp(dx/r,-1,1);session.moveY=clamp(dy/r,-1,1);
        }
        private void updateAim(float x,float y,boolean active) {
            float cx=width-dp(232), cy=height-dp(92), r=dp(54), dx=x-cx,dy=cy-y, dist=(float)Math.sqrt(dx*dx+dy*dy);
            if(dist>r) {dx*=r/dist;dy*=r/dist;}
            session.aimX=clamp(dx/r,-1,1);session.aimY=clamp(dy/r,-1,1);
            session.aimActive=dist>dp(8);
        }
        private float clamp(float v,float min,float max) { return Math.max(min,Math.min(max,v)); }
    }

    private static final class GameSession {
        final String[] cityNames={"СЕРЫЙ ЦЕНТР","РЖАВЫЙ ПОРТ","СОСНОВКА","НЕОН-СИТИ","ЖЕЛЕЗНЫЙ РАЙОН","СТОЛИЦА КАРАНТИНА"};
        final String[] gunNames={"GLOCK 17","ПОМПОВИК","SMG-9","AR-15","M700"};
        final int[] gunDamage={24,15,15,30,92}, gunRate={250,620,100,145,820}, gunMag={12,6,30,30,5};
        final float[] gunCone={0.10f,0.32f,0.16f,0.09f,0.035f};
        final int[] cityGround={0xff414b43,0xff62554a,0xff394c3c,0xff3b454e,0xff514c45,0xff4a3f48};
        final int[] buildingColor={0xff6b716e,0xff97715b,0xff60715e,0xff627a8c,0xff766d62,0xff735e70};
        final int[] cityAccent={0xff7bb2ac,0xffd38a55,0xffa2b57a,0xff58c9d8,0xffd39b58,0xffd85858};
        final SharedPreferences prefs;
        final ArrayList<Building> buildings=new ArrayList<>();
        final ArrayList<Pickup> pickups=new ArrayList<>();
        final ArrayList<Zombie> zombies=new ArrayList<>();
        final ArrayList<Projectile> projectiles=new ArrayList<>();
        final ArrayList<Trace> traces=new ArrayList<>();
        final Random rng=new Random(184927L);
        boolean worldGenerated=false,inside=false, firing=false,sprinting=false,aimActive=false, dead=false;
        float px=12,pz=12,yaw=0,moveX=0,moveY=0,aimX=0,aimY=0;
        float oldX=12,oldZ=12,walkPhase=0,damageMultiplier=1f,speedBonus=0f;
        int level=1,xp=0,hp=100,maxHp=100,armor=25,cash=140,kills=0,skillPoints=0;
        int weapon=0,medkits=2,lootedCount=0,cityKillsTotal=0,perkSelected=0;
        int[] mag={12,0,0,0,0},reserve={72,0,0,0,0};
        boolean[] unlocked={true,false,false,false,false};
        int[] cityKills=new int[6];
        boolean[] cityCleared=new boolean[6], lootSpawned=new boolean[30];
        String notice="Найди припасы. Шесть городов ждут.", message="";
        long noticeUntil=System.currentTimeMillis()+7000,lastUpdate=System.currentTimeMillis(),lastSave=0,lastFire=0,lastMobSpawn=0,lastDamage=0,damageFlashUntil=0,lastInteract=0;
        int insideBuilding=-1,insideCity=0;
        float insideExitX=0,insideExitZ=0;
        boolean missionRewarded=false;
        GameSession() {
            prefs=getPreferences();
            load();
        }
        private SharedPreferences getPreferences() {
            // Uses the application's named save; Activity context is replaced in onCreate after construction.
            return ZombieActivity.this.getSharedPreferences("dead_district_save", MODE_PRIVATE);
        }
        int nextLevelXp(){return 120+(level-1)*95;}
        String cityName(int i){return cityNames[Math.max(0,Math.min(5,i))];}
        String weaponName(int i){return gunNames[Math.max(0,Math.min(4,i))];}
        int cityAt(float x,float z) {
            int cx=(int)Math.floor(x/235f), cz=(int)Math.floor(z/235f);
            cx=Math.max(0,Math.min(2,cx));cz=Math.max(0,Math.min(1,cz));
            return cz*3+cx;
        }
        String objectiveText() {
            int ci=cityAt(px,pz);
            if(inside) return "ОБЫСК: аптечки • патроны • оружие • броня";
            if(cityCleared[ci]) return "РАЙОН ОЧИЩЕН • ИЩИ СНАРЯЖЕНИЕ И ПЕРЕХОДИ ДАЛЬШЕ";
            return "ЗАРАЖЁННЫЕ: "+cityKills[ci]+"/25  •  ЛУТАЙ ДОМА И УЛИЦЫ";
        }
        void toast(String s) {notice=s;noticeUntil=System.currentTimeMillis()+3600;}
        void generateWorld() {
            if(worldGenerated)return;
            worldGenerated=true;
            Random r=new Random(431990L);
            for(int city=0;city<6;city++) {
                int ox=(city%3)*235, oz=(city/3)*235;
                for(int iz=0;iz<8;iz++) for(int ix=0;ix<8;ix++) {
                    int poi=poiAt(ix,iz);
                    float cx=ox+24+ix*24, cz=oz+24+iz*24;
                    if(poi>=0) {
                        float h=poi==0?15:poi==1?12:poi==2?10:poi==3?17:11;
                        int cc=poi==0?0xffa9bec0:poi==1?0xff737d83:poi==2?0xff8a6147:poi==3?0xff9b8e80:0xff81775f;
                        buildings.add(new Building(city*5+poi,city,poi,cx,cz,poi==3?13:12,poi==3?14:12,h,cc,r.nextInt(5)));
                    } else {
                        float x1=cx-5.1f+(r.nextFloat()-0.5f)*1.2f;
                        float z1=cz+(r.nextFloat()-0.5f)*2f;
                        addGenericBuilding(r,city,x1,z1);
                        if(r.nextFloat()<0.88f) {
                            float x2=cx+5.0f+(r.nextFloat()-0.5f)*1.0f;
                            float z2=cz+(r.nextFloat()-0.5f)*2.2f;
                            addGenericBuilding(r,city,x2,z2);
                        } else if(r.nextFloat()<0.40f) {
                            addStreetTree(buildings,city,cx+(r.nextFloat()-0.5f)*12,cz+7+(r.nextFloat()-0.5f)*6,r);
                        }
                    }
                }
            }
            int id=0;
            for(int city=0;city<6;city++) {
                int ox=(city%3)*235, oz=(city/3)*235;
                for(int n=0;n<31;n++) {
                    float x=ox+12+r.nextInt(9)*24, z=oz+12+r.nextInt(9)*24;
                    if(isBlocked(x,z,0.65f)) continue;
                    int kind;
                    if(n==0) kind=3+Math.min(4,city/1);
                    else if(n%9==0) kind=0;
                    else if(n%7==0) kind=2;
                    else if(n%4==0) kind=1;
                    else if(n%5==0) kind=8;
                    else if(n%6==0) kind=9;
                    else kind=r.nextInt(3)==0?1:8;
                    Pickup pick=new Pickup(id++,x,z,kind,kind==8?25+r.nextInt(50):1,-1,city);
                    pick.taken=prefs.getBoolean("street_"+pick.id,false);
                    pickups.add(pick);
                }
            }
            // Extra supplies near the first streets ensure the opening minutes are fair.
            addFixedPickup(6001,12,36,0,1,-1,0);
            addFixedPickup(6002,36,12,1,1,-1,0);
            addFixedPickup(6003,60,36,3,1,-1,0);
            for(int i=0;i<18;i++) spawnZombieAround(0,18+i*1.4f);
        }
        private int poiAt(int x,int z) {
            if(x==1&&z==1)return 0; // hospital
            if(x==6&&z==1)return 1; // police
            if(x==1&&z==6)return 2; // gun store
            if(x==6&&z==6)return 3; // apartments
            if(x==3&&z==3)return 4; // warehouse
            return -1;
        }
        private void addGenericBuilding(Random r,int city,float x,float z) {
            float w=5.5f+r.nextFloat()*3.6f,d=6.6f+r.nextFloat()*4.4f,h=5.5f+r.nextFloat()*(city<3?10.5f:17f);
            int base=buildingColor[city];
            int tint=r.nextInt(37)-18;
            int color=Color.rgb(clampI(Color.red(base)+tint,15,255),clampI(Color.green(base)+tint,15,255),clampI(Color.blue(base)+tint,15,255));
            buildings.add(new Building(-1,city,-1,x,z,w,d,h,color,r.nextInt(8)));
        }
        private void addStreetTree(ArrayList<Building> ignored,int city,float x,float z,Random r) { }
        private int clampI(int v,int a,int b){return Math.max(a,Math.min(b,v));}
        private void addFixedPickup(int id,float x,float z,int kind,int value,int interior,int city) {
            Pickup p=new Pickup(id,x,z,kind,value,interior,city);p.taken=prefs.getBoolean("street_"+id,false);pickups.add(p);
        }
        void spawnZombieAround(int city,float minDist) {
            for(int tries=0;tries<40;tries++) {
                float a=rng.nextFloat()*(float)Math.PI*2;
                float d=minDist+rng.nextFloat()*14;
                float x=px+(float)Math.sin(a)*d,z=pz+(float)Math.cos(a)*d;
                if(x<1||z<1||x>700||z>465||isBlocked(x,z,0.7f))continue;
                zombies.add(new Zombie(x,z,city,rng.nextInt(Math.min(5,2+city/1))));
                return;
            }
            int c=Math.max(0,Math.min(5,city)),ox=(c%3)*235,oz=(c/3)*235;
            for(int i=0;i<30;i++) {
                float x=ox+12+rng.nextInt(9)*24,z=oz+12+rng.nextInt(9)*24;
                if(!isBlocked(x,z,0.7f)&&distance(x,z,px,pz)>7){zombies.add(new Zombie(x,z,city,rng.nextInt(Math.min(5,2+city))));return;}
            }
        }
        boolean isBlocked(float x,float z,float radius) {
            for(Building b:buildings) {
                if(Math.abs(x-b.x)>b.w*0.5f+radius || Math.abs(z-b.z)>b.d*0.5f+radius) continue;
                return true;
            }
            return false;
        }
        void update(float dt) {
            long now=System.currentTimeMillis();
            dt=Math.min(0.05f,Math.max(0,dt));
            if(dead) {if(now-lastSave>2500){save();lastSave=now;}return;}
            if(!inside) {
                float mx=moveX,my=moveY, magMove=(float)Math.sqrt(mx*mx+my*my);
                if(magMove>1){mx/=magMove;my/=magMove;}
                if(aimActive && Math.sqrt(aimX*aimX+aimY*aimY)>0.13) yaw=(float)Math.atan2(aimX,aimY);
                else if(magMove>0.18f) {
                    float worldX=mx*(float)Math.cos(yaw)+my*(float)Math.sin(yaw);
                    float worldZ=mx*(float)Math.sin(yaw)-my*(float)Math.cos(yaw);
                    yaw=(float)Math.atan2(worldX,-worldZ);
                }
                float moveSpeed=(sprinting?7.8f:4.7f)+speedBonus;
                float wx=mx*(float)Math.cos(yaw)+my*(float)Math.sin(yaw);
                float wz=mx*(float)Math.sin(yaw)-my*(float)Math.cos(yaw);
                float nx=px+wx*moveSpeed*dt,nz=pz+wz*moveSpeed*dt;
                if(!isBlocked(nx,pz,0.55f))px=nx;
                if(!isBlocked(px,nz,0.55f))pz=nz;
                px=Math.max(2,Math.min(704,px));pz=Math.max(2,Math.min(468,pz));
                if(magMove>0.12f)walkPhase+=dt*(sprinting?12:8);
                if(firing) shoot(now);
                if(now-lastMobSpawn>1250) {lastMobSpawn=now;if(zombies.size()<34)spawnZombieAround(cityAt(px,pz),25);}
                updateZombies(dt,now);
                updateProjectiles(dt,now);
                updatePickups();
            } else {
                updatePickups();
            }
            for(int i=traces.size()-1;i>=0;i--){traces.get(i).life-=dt;if(traces.get(i).life<=0)traces.remove(i);}
            for(int i=zombies.size()-1;i>=0;i--)if(!zombies.get(i).alive)zombies.remove(i);
            if(hp<=0)die();
            if(now-lastSave>6000){save();lastSave=now;}
            lastUpdate=now;
        }
        private void updateZombies(float dt,long now) {
            for(Zombie z:zombies) {
                if(!z.alive)continue;
                float dx=px-z.x,dz=pz-z.z,dist=(float)Math.sqrt(dx*dx+dz*dz);
                if(dist>42)continue;
                z.phase+=dt*(z.type==1?10:z.type==2?5:7);
                if(dist>1.35f && dist>0.01f) {
                    float speed=z.type==1?2.45f:z.type==2?1.08f:z.type==4?1.36f:1.65f;
                    float vx=dx/dist*speed,vz=dz/dist*speed;
                    if(z.type==4 && dist<14) {
                        if(now>z.attackAt){z.attackAt=now+2100;projectiles.add(new Projectile(z.x,z.z,dx/dist*7f,dz/dist*7f,11,true));}
                    } else {
                        float xx=z.x+vx*dt,zz=z.z+vz*dt;
                        if(!isBlocked(xx,z.z,0.5f))z.x=xx;
                        else if(!isBlocked(z.x,zz,0.5f))z.z=zz;
                        else {float sidestep=(rng.nextBoolean()?1:-1)*speed*dt; if(!isBlocked(z.x+sidestep,z.z,0.5f))z.x+=sidestep;else if(!isBlocked(z.x,z.z+sidestep,0.5f))z.z+=sidestep;}
                    }
                    z.yaw=(float)Math.atan2(dx,-dz);
                } else if(now>z.attackAt) {
                    z.attackAt=now+(z.type==1?700:1050);
                    hurt(z.type==2?20:z.type==3?15:9);
                }
            }
        }
        private void updateProjectiles(float dt,long now) {
            for(int i=projectiles.size()-1;i>=0;i--) {
                Projectile q=projectiles.get(i);q.life-=dt;q.x+=q.vx*dt;q.z+=q.vz*dt;
                if(q.hostile && distance(q.x,q.z,px,pz)<0.7f){hurt(q.damage);q.life=0;}
                if(q.life<=0)projectiles.remove(i);
            }
        }
        private void updatePickups() {
            for(Pickup p:pickups) {
                if(p.taken || (inside ? p.insideBuilding!=insideBuilding : p.insideBuilding!=-1))continue;
                if(distance(px,pz,p.x,p.z)<1.25f)collect(p);
            }
        }
        private void collect(Pickup p) {
            p.taken=true;lootedCount++;
            switch(p.kind) {
                case 0: medkits=Math.min(8,medkits+1);toast("АПТЕЧКА +1");break;
                case 1:
                    for(int i=0;i<5;i++)if(unlocked[i])reserve[i]+=i==4?5:24;
                    toast("ПАТРОНЫ ПОДОБРАНЫ");break;
                case 2: armor=Math.min(100,armor+35);toast("БРОНЕПЛИТА +35");break;
                case 3: case 4: case 5: case 6: case 7:
                    int gun=p.kind-3;
                    if(gun>=0&&gun<5){unlocked[gun]=true;reserve[gun]+=gunMag[gun]*3; if(mag[gun]==0)mag[gun]=gunMag[gun]; weapon=gun;toast("НОВОЕ ОРУЖИЕ: "+gunNames[gun]);}
                    break;
                case 8: cash+=p.value;toast("НАЙДЕНО: $"+p.value);break;
                case 9: hp=Math.min(maxHp,hp+14);toast("ЭНЕРГЕТИК: +14 HP");break;
                case 10: armor=Math.min(100,armor+20);cash+=10;toast("РАЗГРУЗКА: +20 БРОНИ");break;
            }
            addXp(12);
            lootedCount++;
            if(p.id>=0 && p.insideBuilding==-1)prefs.edit().putBoolean("street_"+p.id,true).apply();
        }
        private void shoot(long now) {
            int g=weapon;
            if(now-lastFire<gunRate[g])return;
            lastFire=now;
            if(mag[g]<=0) {toast("НЕТ ПАТРОНОВ — НАЖМИ R");firing=false;return;}
            mag[g]--;
            float dir=yaw;
            int pellets=g==1?6:1;
            boolean hitAny=false;
            for(int n=0;n<pellets;n++) {
                float spread=pellets==1?0:((n-(pellets-1)*0.5f)/(pellets-1))*0.34f;
                float a=dir+spread;
                Zombie target=nearestTarget(a, g==4?55:g==1?15:38, gunCone[g]);
                float tx=px+(float)Math.sin(a)*(g==4?48:g==1?12:34);
                float tz=pz-(float)Math.cos(a)*(g==4?48:g==1?12:34);
                if(target!=null) {tx=target.x;tz=target.z;int damage=(int)(gunDamage[g]*damageMultiplier/(g==1?1:1));if(g==1)damage=gunDamage[g];target.hp-=damage;hitAny=true;
                    if(target.hp<=0)killZombie(target);
                }
                traces.add(new Trace(px+(float)Math.sin(a)*0.6f,pz-(float)Math.cos(a)*0.6f,tx,tz,g==4?0xffffe1a0:0xffffc979,0.11f));
            }
            if(hitAny){/* hit feedback comes from damage and kill messages */}
        }
        private Zombie nearestTarget(float dir,float range,float cone) {
            Zombie best=null;float bd=range;
            for(Zombie z:zombies) {
                if(!z.alive)continue;
                float d=distance(px,pz,z.x,z.z);if(d>bd||d<0.25f)continue;
                float a=(float)Math.atan2(z.x-px,-(z.z-pz)),delta=angleDiff(a,dir);
                if(Math.abs(delta)>cone)continue;
                if(lineBlocked(px,pz,z.x,z.z))continue;
                bd=d;best=z;
            }
            return best;
        }
        private boolean lineBlocked(float x,float z,float tx,float tz) {
            float dx=tx-x,dz=tz-z,len=(float)Math.sqrt(dx*dx+dz*dz);
            int steps=Math.max(2,(int)(len/1.8f));
            for(int i=1;i<steps;i++){float f=i/(float)steps;if(isBlocked(x+dx*f,z+dz*f,0.10f))return true;}
            return false;
        }
        private float angleDiff(float a,float b) {
            float d=a-b;while(d>(float)Math.PI)d-=2*(float)Math.PI;while(d<-(float)Math.PI)d+=2*(float)Math.PI;return d;
        }
        private void killZombie(Zombie z) {
            if(!z.alive)return;z.alive=false;kills++;cityKills[z.city]++;addXp(32+z.type*8);
            cash+=8+rng.nextInt(18);
            if(cityKills[z.city]>=25&&!cityCleared[z.city]) {
                cityCleared[z.city]=true;cash+=250;addXp(160);toast("РАЙОН ОЧИЩЕН! БОНУС $250");
            } else toast(z.type==3?"БРОНЕЗОМБИ УНИЧТОЖЕН":"ЗОМБИ УБИТ");
            if(rng.nextFloat()<0.24f) {
                int kind=rng.nextFloat()<0.28f?0:rng.nextFloat()<0.68f?1:8;
                pickups.add(new Pickup(20000+kills,z.x,z.z,kind,kind==8?15+rng.nextInt(35):1,inside?insideBuilding:-1,z.city));
            }
            if(z.type==2&&rng.nextFloat()<0.5f) pickups.add(new Pickup(30000+kills,z.x+0.45f,z.z+0.4f,2,1,inside?insideBuilding:-1,z.city));
        }
        private void addXp(int amount) {
            xp+=amount;
            while(xp>=nextLevelXp()) {
                xp-=nextLevelXp();level++;skillPoints++;maxHp+=8;hp=Math.min(maxHp,hp+25);
                toast("УРОВЕНЬ "+level+"! +1 ОЧКО НАВЫКА");
            }
        }
        private void hurt(int amount) {
            long now=System.currentTimeMillis();
            if(now-lastDamage<380)return;lastDamage=now;damageFlashUntil=now+170;
            int absorb=Math.min(armor,(int)(amount*0.65f));armor-=absorb;hp-=amount-absorb;
            toast("ПОЛУЧЕН УРОН -"+amount);
        }
        private void die() {dead=true;hp=0;toast("ТЫ ПОГИБ • НАЖМИ USE ДЛЯ ВОЗРОЖДЕНИЯ");}
        synchronized void reload() {
            if(dead)return;
            int g=weapon,need=gunMag[g]-mag[g],n=Math.min(need,reserve[g]);
            if(n<=0){toast("ПЕРЕЗАРЯДКА НЕ НУЖНА");return;}
            mag[g]+=n;reserve[g]-=n;toast("ПЕРЕЗАРЯЖЕНО: "+mag[g]+"/"+reserve[g]);
        }
        synchronized void useMedkit() {
            if(dead){respawn();return;}
            if(medkits<=0){toast("НЕТ АПТЕЧЕК");return;}
            if(hp>=maxHp){toast("ЗДОРОВЬЕ УЖЕ ПОЛНОЕ");return;}
            medkits--;hp=Math.min(maxHp,hp+55);toast("АПТЕЧКА: +55 HP");
        }
        synchronized void cycleWeapon() {
            if(dead)return;
            for(int i=1;i<=5;i++){int n=(weapon+i)%5;if(unlocked[n]){weapon=n;toast("ОРУЖИЕ: "+gunNames[n]);return;}}
            toast("НАЙДИ ДРУГОЕ ОРУЖИЕ");
        }
        synchronized void upgrade() {
            if(skillPoints<=0){perkSelected=(perkSelected+1)%4;toast("ПЕРК: "+new String[]{"ЗДОРОВЬЕ","УРОН","СКОРОСТЬ","БРОНЯ"}[perkSelected]+" • НУЖНО ОЧКО");return;}
            skillPoints--;
            if(perkSelected==0){maxHp+=25;hp=Math.min(maxHp,hp+25);toast("ПРОКАЧКА: +25 МАКС. ЗДОРОВЬЯ");}
            else if(perkSelected==1){damageMultiplier+=0.13f;toast("ПРОКАЧКА: +13% УРОН");}
            else if(perkSelected==2){speedBonus+=0.45f;toast("ПРОКАЧКА: +0.45 СКОРОСТЬ");}
            else {armor=Math.min(100,armor+45);toast("ПРОКАЧКА: +45 БРОНИ");}
            perkSelected=(perkSelected+1)%4;save();
        }
        synchronized void interact() {
            long now=System.currentTimeMillis();if(now-lastInteract<280)return;lastInteract=now;
            if(dead){respawn();return;}
            if(inside) {
                if(pz>7.0f){exitInterior();return;}
                Pickup best=null;float bd=3.0f;
                for(Pickup p:pickups)if(!p.taken&&p.insideBuilding==insideBuilding){float d=distance(px,pz,p.x,p.z);if(d<bd){best=p;bd=d;}}
                if(best!=null)collect(best);else toast("ОБЫСКАЙ КОМНАТУ: ИЩИ СВЕТЯЩИЙСЯ ЛУТ");
                return;
            }
            Building near=null;float bestDist=6.3f;
            for(Building b:buildings)if(b.poi>=0) {
                float dx=px-b.doorX(),dz=pz-b.doorZ(),d=(float)Math.sqrt(dx*dx+dz*dz);
                if(d<bestDist){near=b;bestDist=d;}
            }
            if(near!=null) {enterInterior(near);return;}
            Pickup nearest=null;bestDist=2.2f;
            for(Pickup p:pickups)if(!p.taken&&p.insideBuilding==-1){float d=distance(px,pz,p.x,p.z);if(d<bestDist){nearest=p;bestDist=d;}}
            if(nearest!=null){collect(nearest);return;}
            toast("ПОДОЙДИ К ДВЕРИ ЗДАНИЯ И НАЖМИ USE");
        }
        private void enterInterior(Building b) {
            inside=true;insideBuilding=b.id;insideCity=b.city;insideExitX=px;insideExitZ=pz;
            px=0;pz=8.2f;yaw=0;moveX=moveY=aimX=aimY=0;aimActive=false;firing=false;
            if(!lootSpawned[b.id]) {
                lootSpawned[b.id]=true;
                int[] kinds;
                if(b.poi==0)kinds=new int[]{0,0,9,1,2,8};
                else if(b.poi==1)kinds=new int[]{1,2,1,8,3,0};
                else if(b.poi==2)kinds=new int[]{3+(Math.min(4,b.city),4,1,1,8,2};
                else if(b.poi==3)kinds=new int[]{0,9,8,1,2,6};
                else kinds=new int[]{1,1,8,2,4,5};
                float[][] loc={{-5,2},{5,2},{-5,-3},{5,-3},{0,-7},{0,0}};
                for(int i=0;i<kinds.length;i++) {
                    int kind=kinds[i];
                    int lootId=100000+b.id*10+i;
                    boolean taken=prefs.getBoolean("street_"+lootId,false);
                    Pickup p=new Pickup(lootId,loc[i][0],loc[i][1],kind,kind==8?35+rng.nextInt(60):1,b.id,b.city);p.taken=taken;pickups.add(p);
                }
            }
            toast("ЗАШЁЛ: "+poiName(b.poi)+" • ОСМОТРИ ВСЕ КОМНАТЫ");
        }
        private String poiName(int poi){return new String[]{"БОЛЬНИЦА","ПОЛИЦИЯ","ОРУЖЕЙНАЯ","ЖИЛОЙ ДОМ","СКЛАД"}[poi];}
        private void exitInterior() {
            inside=false;px=insideExitX;pz=insideExitZ;yaw=0;moveX=moveY=aimX=aimY=0;aimActive=false;firing=false;
            toast("ВЫШЕЛ НА УЛИЦУ • СЛЕДИ ЗА ЗОМБИ");
        }
        private void respawn() {
            dead=false;hp=Math.max(35,maxHp/2);px=12;pz=12;inside=false;insideBuilding=-1;armor=Math.max(0,armor-15);firing=false;
            toast("ВОЗРОЖДЕНИЕ • СОХРАНИ ПАТРОНЫ");
        }
        private float distance(float x,float z,float xx,float zz) {float dx=x-xx,dz=z-zz;return (float)Math.sqrt(dx*dx+dz*dz);}
        void save() {
            SharedPreferences.Editor e=prefs.edit().putFloat("px",px).putFloat("pz",pz).putFloat("yaw",yaw).putInt("level",level).putInt("xp",xp)
                .putInt("hp",hp).putInt("maxHp",maxHp).putInt("armor",armor).putInt("cash",cash).putInt("kills",kills).putInt("skillPoints",skillPoints)
                .putInt("medkits",medkits).putInt("weapon",weapon).putFloat("damageMult",damageMultiplier).putFloat("speedBonus",speedBonus).putInt("looted",lootedCount);
            for(int i=0;i<5;i++)e.putBoolean("gun"+i,unlocked[i]).putInt("mag"+i,mag[i]).putInt("reserve"+i,reserve[i]);
            for(int i=0;i<6;i++)e.putInt("cityKills"+i,cityKills[i]).putBoolean("cityClear"+i,cityCleared[i]);
            for(int i=0;i<30;i++)e.putBoolean("lootSpawned"+i,lootSpawned[i]);
            e.apply();
        }
        private void load() {
            px=prefs.getFloat("px",12);pz=prefs.getFloat("pz",12);yaw=prefs.getFloat("yaw",0);level=prefs.getInt("level",1);xp=prefs.getInt("xp",0);
            hp=prefs.getInt("hp",100);maxHp=prefs.getInt("maxHp",100);armor=prefs.getInt("armor",25);cash=prefs.getInt("cash",140);kills=prefs.getInt("kills",0);skillPoints=prefs.getInt("skillPoints",0);
            medkits=prefs.getInt("medkits",2);weapon=prefs.getInt("weapon",0);damageMultiplier=prefs.getFloat("damageMult",1f);speedBonus=prefs.getFloat("speedBonus",0f);lootedCount=prefs.getInt("looted",0);
            for(int i=0;i<5;i++){unlocked[i]=prefs.getBoolean("gun"+i,i==0);mag[i]=prefs.getInt("mag"+i,i==0?12:0);reserve[i]=prefs.getInt("reserve"+i,i==0?72:0);}
            for(int i=0;i<6;i++){cityKills[i]=prefs.getInt("cityKills"+i,0);cityCleared[i]=prefs.getBoolean("cityClear"+i,false);}
            for(int i=0;i<30;i++)lootSpawned[i]=prefs.getBoolean("lootSpawned"+i,false);
        }
    }

    private static final class Building {
        final int id,city,poi;final float x,z,w,d,h;final int color,variant;
        Building(int id,int city,int poi,float x,float z,float w,float d,float h,int color,int variant) {
            this.id=id;this.city=city;this.poi=poi;this.x=x;this.z=z;this.w=w;this.d=d;this.h=h;this.color=color;this.variant=variant;
        }
        float doorX(){return x;}
        float doorZ(){return z+d*0.5f+1.6f;}
    }
    private static final class Pickup {
        final int id,kind,value,insideBuilding,city;final float x,z;boolean taken;
        Pickup(int id,float x,float z,int kind,int value,int insideBuilding,int city){this.id=id;this.x=x;this.z=z;this.kind=kind;this.value=value;this.insideBuilding=insideBuilding;this.city=city;}
    }
    private static final class Zombie {
        float x,z,yaw,phase;int hp,maxHp,type,city;boolean alive=true;long attackAt=System.currentTimeMillis()+700;
        Zombie(float x,float z,int city,int type){this.x=x;this.z=z;this.city=city;this.type=type;maxHp=52+city*19+type*24;hp=maxHp;}
    }
    private static final class Projectile {
        float x,z,vx,vz,life=3.2f;int damage;boolean hostile;
        Projectile(float x,float z,float vx,float vz,int dmg,boolean hostile){this.x=x;this.z=z;this.vx=vx;this.vz=vz;damage=dmg;this.hostile=hostile;}
    }
    private static final class Trace {
        final float x,z,tx,tz;final int color;float life;
        Trace(float x,float z,float tx,float tz,int color,float life){this.x=x;this.z=z;this.tx=tx;this.tz=tz;this.color=color;this.life=life;}
    }

    private static final class CityRenderer implements GLSurfaceView.Renderer {
        final GameSession s;
        int program, aPosition, aColor, uMvp, staticVbo, dynamicVbo;
        Mesh world, interior;
        int interiorCacheKey=-1;
        int width=1,height=1;
        long lastFrame=System.currentTimeMillis();
        final Matrix4 mat=new Matrix4();
        final MeshBuilder dynamic=new MeshBuilder(24000);
        CityRenderer(GameSession session){s=session;}
        @Override public void onSurfaceCreated(GL10 gl,EGLConfig config) {
            GLES20.glClearColor(0.035f,0.055f,0.055f,1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDepthFunc(GLES20.GL_LEQUAL);
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            program=makeProgram(VERT,FRAG);
            aPosition=GLES20.glGetAttribLocation(program,"aPosition");
            aColor=GLES20.glGetAttribLocation(program,"aColor");
            uMvp=GLES20.glGetUniformLocation(program,"uMvp");
            if(!s.worldGenerated)s.generateWorld();
            world=buildWorld();
            world.upload();
            lastFrame=System.currentTimeMillis();
        }
        @Override public void onSurfaceChanged(GL10 gl,int w,int h) {
            width=Math.max(1,w);height=Math.max(1,h);GLES20.glViewport(0,0,width,height);
        }
        @Override public void onDrawFrame(GL10 gl) {
            long now=System.currentTimeMillis();float dt=Math.min(0.05f,(now-lastFrame)/1000f);lastFrame=now;
            synchronized(s) {
                s.update(dt);
                if(s.inside) {
                    int key=s.insideBuilding;
                    if(interiorCacheKey!=key){interior=buildInterior(s);interior.upload();interiorCacheKey=key;}
                } else interiorCacheKey=-1;
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
                float[] view=new float[16],proj=new float[16],mvp=new float[16];
                float eyeX,eyeY,eyeZ,targetX=s.px,targetY=s.inside?1.0f:1.1f,targetZ=s.pz;
                if(s.inside) {
                    eyeX=s.px-(float)Math.sin(s.yaw)*7.2f;eyeY=4.0f;eyeZ=s.pz+(float)Math.cos(s.yaw)*7.2f;
                } else {
                    eyeX=s.px-(float)Math.sin(s.yaw)*9.2f;eyeY=6.4f;eyeZ=s.pz+(float)Math.cos(s.yaw)*9.2f;
                }
                mat.lookAt(view,eyeX,eyeY,eyeZ,targetX,targetY,targetZ,0,1,0);
                mat.perspective(proj,s.inside?62:59,width/(float)height,0.12f,380f);
                mat.multiply(mvp,proj,view);
                GLES20.glUseProgram(program);
                GLES20.glUniformMatrix4fv(uMvp,1,false,mvp,0);
                drawMesh(s.inside?interior:world);
                buildDynamic(now);
                Mesh dm=dynamic.finish();
                drawMesh(dm);
                if(s.dead) {
                    // A subtle red-tinted sky/floor remains visible; the HUD offers revive through USE.
                }
            }
        }
        private int makeProgram(String vs,String fs) {
            int v=shader(GLES20.GL_VERTEX_SHADER,vs),f=shader(GLES20.GL_FRAGMENT_SHADER,fs);
            int p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);
            int[] status=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,status,0);
            if(status[0]==0)throw new RuntimeException("3D shader link failed: "+GLES20.glGetProgramInfoLog(p));
            return p;
        }
        private int shader(int type,String src) {
            int sh=GLES20.glCreateShader(type);GLES20.glShaderSource(sh,src);GLES20.glCompileShader(sh);
            int[] ok=new int[1];GLES20.glGetShaderiv(sh,GLES20.GL_COMPILE_STATUS,ok,0);
            if(ok[0]==0)throw new RuntimeException("3D shader compile failed: "+GLES20.glGetShaderInfoLog(sh));
            return sh;
        }
        private void drawMesh(Mesh mesh) {
            if(mesh==null||mesh.count==0)return;
            FloatBuffer b=mesh.data;b.position(0);
            GLES20.glVertexAttribPointer(aPosition,3,GLES20.GL_FLOAT,false,24,b);
            GLES20.glEnableVertexAttribArray(aPosition);
            b.position(3);
            GLES20.glVertexAttribPointer(aColor,3,GLES20.GL_FLOAT,false,24,b);
            GLES20.glEnableVertexAttribArray(aColor);
            b.position(0);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,mesh.count);
        }

        private Mesh buildWorld() {
            MeshBuilder m=new MeshBuilder(850000);
            Random r=new Random(73831L);
            for(int city=0;city<6;city++) {
                int ox=(city%3)*235,oz=(city/3)*235;
                int ground=s.cityGround[city];
                m.box(ox+110,-0.30f,oz+110,220,0.6f,220,ground);
                // Wide streets, broken lane markers, crossings and sidewalks.
                for(int i=0;i<9;i++) {
                    float line=ox+12+i*24;
                    m.box(line,0.035f,oz+110,5.9f,0.07f,220,0xff303637);
                    m.box(ox+110,0.036f,oz+12+i*24,220,0.072f,5.9f,0xff303637);
                    for(int k=0;k<10;k++) {
                        float z=oz+12+k*22;
                        m.box(line,0.081f,z,0.12f,0.018f,7.0f,0xff858477);
                        float x=ox+12+k*22;
                        m.box(x,0.082f,oz+12+i*24,7.0f,0.018f,0.12f,0xff858477);
                    }
                    // Pavement edges / curbs.
                    m.box(line-3.1f,0.10f,oz+110,0.25f,0.18f,220,0xff8b8b7d);
                    m.box(line+3.1f,0.10f,oz+110,0.25f,0.18f,220,0xff8b8b7d);
                }
                for(int ix=0;ix<8;ix++)for(int iz=0;iz<8;iz++) {
                    float cx=ox+24+ix*24,cz=oz+24+iz*24;
                    Building main=null;
                    for(Building b:s.buildings)if(b.city==city&&b.poi>=0&&Math.abs(b.x-cx)<0.2f&&Math.abs(b.z-cz)<0.2f){main=b;break;}
                    // Each address has a paved apron and a detailed building envelope.
                    for(Building b:s.buildings) {
                        if(b.city!=city)continue;
                        if(Math.abs(b.x-cx)>12.5f||Math.abs(b.z-cz)>12.5f)continue;
                        m.box(b.x,0.10f,b.z,b.w+1.7f,0.16f,b.d+1.7f,city==2?0xff777b6b:0xff77766e);
                        addBuildingMesh(m,b,r);
                    }
                }
                addCityStreetProps(m,city,ox,oz,r);
            }
            // Inter-city connectors and highway barriers.
            for(int row=0;row<2;row++) {
                float z=row*235+110;
                for(int col=0;col<2;col++) {
                    float x=col*235+227.5f;
                    m.box(x,0.04f,z,15.5f,0.08f,7.5f,0xff353a3b);
                    for(int i=0;i<5;i++)m.box(x,0.09f,z-3.0f+i*1.5f,1.1f,0.025f,0.08f,0xffb4a57a);
                }
            }
            float[] data=m.data;return new Mesh(data,m.n);
        }
        private void addBuildingMesh(MeshBuilder m,Building b,Random r) {
            int wall=b.color;
            int roof=shade(b.color,0.55f);
            m.box(b.x,b.h*0.5f,b.z,b.w,b.h,b.d,wall);
            m.box(b.x,b.h+0.18f,b.z,b.w+0.38f,0.35f,b.d+0.38f,roof);
            // Roof parapets, air vents and water tanks.
            m.box(b.x,b.h+0.42f,b.z-b.d*0.46f,b.w+0.22f,0.18f,0.22f,shade(wall,0.72f));
            m.box(b.x-b.w*0.22f,b.h+0.72f,b.z+b.d*0.12f,1.1f,0.68f,1.3f,0xff606b69);
            if(b.h>13)m.box(b.x+b.w*0.22f,b.h+0.55f,b.z-b.d*0.1f,0.65f,0.42f,0.72f,0xff7d8780);
            // Recessed windows on all four facades.
            int win=b.city==3?0xff8ad1d0:b.city==5?0xffd68a70:b.city==2?0xffa5bc91:0xffa6b6b6;
            if(b.variant%4==0)win=0xff53676a;
            float winY=1.7f;
            while(winY<b.h-1.0f) {
                for(float xx:new float[]{-b.w*0.27f,b.w*0.27f}) {
                    m.box(b.x+xx,winY,b.z+b.d*0.5f+0.045f,Math.max(0.55f,b.w*0.18f),0.74f,0.075f,win);
                    m.box(b.x+xx,winY,b.z-b.d*0.5f-0.045f,Math.max(0.55f,b.w*0.18f),0.74f,0.075f,shade(win,0.75f));
                }
                for(float zz:new float[]{-b.d*0.24f,b.d*0.24f}) {
                    m.box(b.x+b.w*0.5f+0.045f,winY,b.z+zz,0.075f,0.74f,Math.max(0.55f,b.d*0.18f),shade(win,0.90f));
                    m.box(b.x-b.w*0.5f-0.045f,winY,b.z+zz,0.075f,0.74f,Math.max(0.55f,b.d*0.18f),win);
                }
                winY+=2.35f;
            }
            // Front entrance, frame and canopy.
            m.box(b.x,1.05f,b.z+b.d*0.5f+0.08f,1.15f,2.1f,0.18f,0xff343a39);
            m.box(b.x,1.05f,b.z+b.d*0.5f+0.19f,0.78f,1.78f,0.10f,b.poi>=0?0xff4d8175:0xff655a4f);
            m.box(b.x,2.18f,b.z+b.d*0.5f+0.24f,1.65f,0.18f,0.52f,b.poi>=0?s.cityAccent[b.city]:0xff4e5550);
            // Facade type-specific signage.
            if(b.poi>=0) {
                int accent=s.cityAccent[b.city];
                m.box(b.x,b.h*0.67f,b.z+b.d*0.5f+0.13f,3.2f,0.72f,0.16f,accent);
                if(b.poi==0) {m.box(b.x, b.h*0.67f,b.z+b.d*0.5f+0.25f,0.35f,0.95f,0.12f,0xfff2f1e8);m.box(b.x,b.h*0.67f,b.z+b.d*0.5f+0.25f,1.0f,0.30f,0.12f,0xfff2f1e8);}
                if(b.poi==1) {m.box(b.x,b.h*0.67f,b.z+b.d*0.5f+0.25f,2.2f,0.13f,0.12f,0xffd8e5e6);}
                if(b.poi==2) {m.box(b.x,b.h*0.55f,b.z+b.d*0.5f+0.4f,4.2f,0.2f,0.75f,0xffb24a3f);}
                if(b.poi==3) {for(int i=-1;i<=1;i++)m.box(b.x+i*3.6f,b.h*0.55f,b.z+b.d*0.5f+0.15f,2.0f,0.12f,0.4f,0xff8e9d9d);}
                if(b.poi==4) {m.box(b.x-b.w*0.27f,b.h+1.45f,b.z,1.0f,1.1f,1.0f,0xff8b8578);m.box(b.x+b.w*0.25f,b.h+1.15f,b.z,0.75f,0.65f,0.78f,0xff9e9583);}
            }
            // Cracked facade detail and air-conditioner units.
            if(b.variant%2==0)for(int i=0;i<2;i++) {
                float xx=b.x+(i==0?-1:1)*b.w*0.35f;
                m.box(xx,1.65f,b.z-b.d*0.5f-0.12f,0.55f,0.42f,0.24f,0xff545d5b);
                m.box(xx,1.69f,b.z-b.d*0.5f-0.25f,0.30f,0.08f,0.07f,0xff262c2b);
            }
            // Broken sign supports, wires and fire escapes.
            if(b.h>10 && b.variant%3==0) {
                for(int floor=1;floor<(int)(b.h/3.2f);floor++) {
                    m.box(b.x+b.w*0.5f+0.28f,floor*3.0f,b.z,0.12f,0.08f,b.d+0.8f,0xff4d5554);
                    m.box(b.x+b.w*0.5f+0.55f,floor*3.0f,b.z,0.12f,0.4f,b.d+0.8f,0xff4d5554);
                }
            }
        }
        private void addCityStreetProps(MeshBuilder m,int city,int ox,int oz,Random r) {
            for(int n=0;n<25;n++) {
                int ix=r.nextInt(9),iz=r.nextInt(9);float x=ox+12+ix*24,z=oz+12+iz*24;
                if(n%4==0) { // parked, abandoned car
                    float yaw=(n%8==0)?(float)Math.PI/2:0;
                    m.boxRot(x,0.58f,z,2.15f,0.78f,4.1f,city%2==0?0xff656c68:0xff7c5042,yaw);
                    m.boxRot(x,1.13f,z-0.1f,1.65f,0.55f,1.9f,0xff3b4a4e,yaw);
                    for(float dx:new float[]{-0.9f,0.9f})for(float dz:new float[]{-1.2f,1.2f})
                        m.boxRot(x+dx,0.30f,z+dz,0.23f,0.43f,0.65f,0xff252929,yaw);
                    m.box(x,1.42f,z+2.1f,0.28f,0.12f,0.08f,0xffa74b41);
                } else if(n%4==1) { // street light
                    m.box(x,2.15f,z,0.15f,4.3f,0.15f,0xff454c49);
                    m.box(x,4.25f,z-0.8f,0.16f,0.13f,1.7f,0xff454c49);
                    m.box(x,4.16f,z-1.55f,0.45f,0.19f,0.28f,city==3?0xff8fe5dc:0xffd8cb8f);
                } else if(n%4==2) { // dumpster and bags
                    m.box(x,0.55f,z,1.35f,1.1f,0.85f,0xff526b60);
                    m.box(x,1.14f,z,1.5f,0.12f,0.94f,0xff303d37);
                    m.box(x-0.55f,0.28f,z+0.65f,0.45f,0.38f,0.43f,0xff565247);
                } else { // concrete barricade
                    m.box(x,0.52f,z,2.1f,1.0f,0.7f,0xff77766c);
                    m.box(x,0.72f,z-0.36f,0.78f,0.28f,0.06f,0xffc29a56);
                }
            }
            // Roadside vegetation and debris in empty gaps.
            for(int n=0;n<34;n++) {
                float x=ox+8+r.nextFloat()*204,z=oz+8+r.nextFloat()*204;
                if(s.isBlocked(x,z,1.0f))continue;
                if(n%3==0) {
                    m.box(x,0.8f,z,0.38f,1.6f,0.38f,0xff59463a);
                    m.box(x,2.0f,z,1.8f,1.55f,1.8f,city==2?0xff526f4b:0xff4b5d4c);
                    m.box(x+0.35f,2.6f,z-0.1f,1.0f,1.1f,1.1f,city==2?0xff627d55:0xff58664f);
                } else {
                    m.box(x,0.24f,z,0.85f,0.48f,0.72f,n%2==0?0xff4d514d:0xff766c5b);
                    if(n%5==0)m.box(x+0.15f,0.55f,z+0.2f,0.2f,0.25f,0.2f,0xffb84c40);
                }
            }
        }

        private Mesh buildInterior(GameSession s) {
            MeshBuilder m=new MeshBuilder(80000);
            Building b=null;for(Building v:s.buildings)if(v.id==s.insideBuilding){b=v;break;}
            int type=b==null?0:b.poi,city=b==null?0:b.city;
            int wall=type==0?0xffb8c0bb:type==1?0xff6e7c82:type==2?0xff806956:type==3?0xff8a8175:0xff827b6d;
            m.box(0,-0.18f,0,24,0.36f,24,0xff5f655d);
            // Floor tiles, damaged linoleum, pooled dark stains and scattered debris.
            for(int x=-10;x<=10;x+=2)for(int z=-10;z<=10;z+=2)
                m.box(x,0.012f,z,1.94f,0.035f,1.94f,((x+z)%4==0)?0xff77786e:0xff696b62);
            m.box(-11,3f,0,0.45f,6f,24,wall);m.box(11,3f,0,0.45f,24,wall);
            m.box(0,3f,-11,22,6f,0.45f,wall);
            m.box(-7.0f,3f,11,8.0f,6f,0.45f,wall);m.box(7.0f,3f,11,8.0f,6f,0.45f,wall);
            m.box(0,5.98f,0,22,0.16f,22,shade(wall,0.78f));
            // Interior corridors and room dividers with openings.
            m.box(-7,2.1f,-1.2f,0.28f,4.2f,18,shade(wall,0.88f));
            m.box(1.4f,2.1f,-5.4f,0.28f,4.2f,11,shade(wall,0.82f));
            m.box(6.0f,2.1f,3.0f,10,4.2f,0.25f,shade(wall,0.90f));
            // Windows, fluorescent tubes and emergency exit frame.
            m.box(-10.73f,3.2f,-5.2f,0.08f,1.55f,2.6f,0xff7ca4a7);
            m.box(10.73f,3.2f,-5.2f,0.08f,1.55f,2.6f,0xff7ca4a7);
            m.box(-1,5.55f,-4.5f,4.0f,0.10f,0.4f,0xffe3dbb3);
            m.box(5,5.55f,4.6f,3.2f,0.10f,0.35f,0xffd8e2ce);
            // Type-specific furniture and interactive-looking loot stations.
            if(type==0) { // hospital beds and cabinets
                for(float z:new float[]{-7,-1.8f,4.3f}) {
                    m.box(-9,0.64f,z,2.0f,0.45f,3.7f,0xffd0d4cc);
                    m.box(-9,0.91f,z-0.65f,1.8f,0.16f,0.72f,0xffc8d4d3);
                    m.box(-9,1.07f,z-1.1f,1.8f,0.22f,0.65f,0xffe2dfd5);
                    m.box(-7.8f,0.92f,z,0.12f,0.78f,3.6f,0xff657a7b);
                }
                for(float z:new float[]{-6,0,6}){m.box(8,1.2f,z,2.4f,2.4f,1.0f,0xff9aaca8);m.box(8,2.48f,z,2.7f,0.14f,1.15f,0xffe4e5d6);}
                m.box(0,2.4f,-9.7f,3.3f,2.1f,1.4f,0xffb5c3c0);
            } else if(type==1) { // police desks, evidence cabinets and bars
                for(float z:new float[]{-7,-2,3}) {
                    m.box(-8,0.85f,z,3.2f,0.2f,1.4f,0xff7e6551);
                    m.box(-8,1.35f,z,0.13f,0.85f,1.4f,0xff4e4a42);
                    m.box(-8,1.83f,z,2.5f,0.18f,1.15f,0xff4a5150);
                }
                for(float x:new float[]{3.6f,5.0f,6.4f,7.8f})m.box(x,1.45f,8.2f,0.12f,2.9f,0.12f,0xff333c3c);
                m.box(5.7f,2.85f,8.2f,4.5f,0.13f,0.14f,0xff333c3c);
                m.box(8,1.8f,-8,2.4f,3.6f,2.5f,0xff565d59);
            } else if(type==2) { // gun-store racks
                for(float x:new float[]{-9,-5,3,7}) {
                    m.box(x,1.4f,-3,0.45f,2.8f,11.5f,0xff423e39);
                    for(int y=0;y<3;y++)m.box(x+0.3f,0.65f+y*0.8f,-3,0.42f,0.12f,11.2f,0xff9a7953);
                    for(int z=-7;z<=1;z+=2)m.box(x+0.48f,0.9f+(z%4==1?0.7f:0),z,0.24f,0.16f,0.85f,0xff282d2d);
                }
                m.box(0,0.86f,8.1f,6.2f,1.55f,1.4f,0xff5e4c3c);
                m.box(0,1.72f,8.1f,6.45f,0.18f,1.6f,0xff9b7650);
            } else if(type==3) { // apartment beds, sofa and kitchen
                m.box(-4,0.65f,-6,3.8f,0.55f,3.2f,0xff75665a);
                m.box(-4,1.02f,-7.25f,3.6f,0.3f,0.7f,0xff8e7c6e);
                m.box(5,0.55f,6,3.6f,0.4f,2.3f,0xff66534a);
                m.box(5,0.92f,6,3.4f,0.45f,1.8f,0xff9c8c7d);
                for(float x:new float[]{-1,1,3})m.box(x,1.1f,-9,1.6f,2.2f,0.7f,0xff7a8279);
                m.box(0,0.52f,0,3.5f,0.95f,1.4f,0xff5c615c);
                m.box(0,1.1f,0,3.7f,0.16f,1.55f,0xff8b8376);
            } else { // warehouse pallets and stacked crates
                for(int i=0;i<4;i++)for(int j=0;j<3;j++)m.box(-8+i*4,0.7f+j*1.0f,-7,3.1f,1.2f,2.8f,((i+j)%2==0)?0xff92734d:0xff716046);
                for(int i=0;i<3;i++)m.box(7,1.0f+i*1.3f,3+i*2.4f,2.6f,1.8f,2.0f,0xff6f7a68);
                m.box(0,0.55f,-0.5f,5.2f,0.6f,3.2f,0xffa39b88);
            }
            // Caution tape and debris give each room a lived-in, looted look.
            m.box(0,0.12f,-10,3.1f,0.08f,0.2f,0xff9d5446);
            m.box(-3,0.14f,5,1.1f,0.08f,0.8f,0xffa58c55);
            m.box(2,0.14f,-2,0.8f,0.08f,0.6f,0xff4f5550);
            m.box(0,4.6f,0,0.16f,1.8f,0.16f,0xff404543);
            return new Mesh(m.data,m.n);
        }

        private void buildDynamic(long now) {
            dynamic.reset();
            if(!s.inside) {
                // Render only nearby pickups and infected; the world itself is a single static mesh.
                for(Pickup p:s.pickups) {
                    if(p.taken||p.insideBuilding!=-1)continue;
                    if(Math.abs(p.x-s.px)>34||Math.abs(p.z-s.pz)>34)continue;
                    float bob=(float)Math.sin(now/260.0+p.id)*0.12f;
                    int co=lootColor(p.kind);
                    dynamic.box(p.x,0.47f+bob,p.z,0.62f,0.66f,0.62f,shade(co,0.75f));
                    dynamic.box(p.x,0.84f+bob,p.z,0.38f,0.10f,0.38f,co);
                    if(p.kind>=3&&p.kind<=7)dynamic.boxRot(p.x,0.98f+bob,p.z,0.18f,0.12f,0.9f,0xff252a28,0.35f);
                }
                for(Zombie z:s.zombies)if(z.alive&&Math.abs(z.x-s.px)<48&&Math.abs(z.z-s.pz)<48)drawZombie(dynamic,z,now);
            } else {
                for(Pickup p:s.pickups)if(!p.taken&&p.insideBuilding==s.insideBuilding) {
                    float bob=(float)Math.sin(now/260.0+p.id)*0.08f;
                    int co=lootColor(p.kind);
                    dynamic.box(p.x,0.48f+bob,p.z,0.62f,0.62f,0.62f,shade(co,0.72f));
                    dynamic.box(p.x,0.83f+bob,p.z,0.39f,0.09f,0.39f,co);
                    if(p.kind>=3&&p.kind<=7)dynamic.boxRot(p.x,0.96f+bob,p.z,0.18f,0.12f,0.86f,0xff202625,0.4f);
                }
            }
            // Player model with gait animation, backpack, helmet, arms, boots and firearm.
            float bob=(float)Math.sin(s.walkPhase*2)*0.045f;
            float rightX=(float)Math.cos(s.yaw),rightZ=(float)Math.sin(s.yaw);
            int jacket=0xff526d5c,skin=0xffb69a7f;
            dynamic.boxRot(s.px,0.95f+bob,s.pz,0.78f,0.90f,0.42f,jacket,s.yaw);
            dynamic.boxRot(s.px,1.43f+bob,s.pz,0.78f,0.75f,0.42f,0xff657e69,s.yaw);
            dynamic.boxRot(s.px-rightX*0.02f,1.99f+bob,s.pz-rightZ*0.02f,0.52f,0.48f,0.48f,skin,s.yaw);
            dynamic.boxRot(s.px-rightX*0.02f,2.23f+bob,s.pz-rightZ*0.02f,0.60f,0.17f,0.55f,0xff4b5c4b,s.yaw);
            dynamic.boxRot(s.px-rightX*0.38f,1.44f+bob+(float)Math.sin(s.walkPhase)*0.15f,s.pz-rightZ*0.38f,0.27f,0.75f,0.28f,0xff60715f,s.yaw);
            dynamic.boxRot(s.px+rightX*0.38f,1.44f+bob-(float)Math.sin(s.walkPhase)*0.15f,s.pz+rightZ*0.38f,0.27f,0.75f,0.28f,0xff60715f,s.yaw);
            dynamic.boxRot(s.px-rightX*0.22f,0.40f+(float)Math.sin(s.walkPhase)*0.12f,s.pz-rightZ*0.22f,0.28f,0.73f,0.31f,0xff353b37,s.yaw);
            dynamic.boxRot(s.px+rightX*0.22f,0.40f-(float)Math.sin(s.walkPhase)*0.12f,s.pz+rightZ*0.22f,0.28f,0.73f,0.31f,0xff353b37,s.yaw);
            // Backpack mounted behind the torso.
            dynamic.boxRot(s.px-(float)Math.sin(s.yaw)*0.32f,1.34f+bob,s.pz+(float)Math.cos(s.yaw)*0.32f,0.52f,0.66f,0.25f,0xff3d5145,s.yaw);
            // Gun held forward, with a muzzle flash during each shot.
            float fx=s.px+(float)Math.sin(s.yaw)*0.76f+rightX*0.22f;
            float fz=s.pz-(float)Math.cos(s.yaw)*0.76f+rightZ*0.22f;
            dynamic.boxRot(fx,1.38f+bob,fz,0.19f,0.19f,s.weapon==4?1.05f:0.72f,0xff202624,s.yaw);
            dynamic.boxRot(fx,1.40f+bob,fz-(float)Math.cos(s.yaw)*0.48f,0.10f,0.10f,0.25f,0xff8a9088,s.yaw);
            if(now-s.lastFire<85)dynamic.box(fx+(float)Math.sin(s.yaw)*0.55f,1.45f,fz-(float)Math.cos(s.yaw)*0.55f,0.28f,0.24f,0.28f,0xffffd88b);
            // Bullet tracers are short-lived 3D streaks.
            for(Trace t:s.traces) {
                float dx=t.tx-t.x,dz=t.tz-t.z,len=(float)Math.sqrt(dx*dx+dz*dz);
                if(len>0.1f&&len<70)dynamic.boxRot((t.x+t.tx)*0.5f,1.3f,(t.z+t.tz)*0.5f,0.065f,0.065f,len,t.color,(float)Math.atan2(dx,-dz));
            }
            for(Projectile p:s.projectiles) {
                dynamic.box(p.x,1.2f,p.z,0.24f,0.22f,0.24f,p.hostile?0xffc44e3b:0xffffd98a);
            }
        }
        private void drawZombie(MeshBuilder m,Zombie z,long now) {
            float sw=(float)Math.sin(z.phase)*0.24f,bob=(float)Math.sin(z.phase*1.4f)*0.055f;
            float rx=(float)Math.cos(z.yaw),rz=(float)Math.sin(z.yaw);
            int body=z.type==0?0xff66715d:z.type==1?0xff74805a:z.type==2?0xff79745c:z.type==3?0xff59666b:0xff607f6c;
            int shirt=z.type==2?0xff7c4d43:z.type==3?0xff55595a:0xff5a6555;
            float scale=z.type==2?1.18f:z.type==3?1.12f:1f;
            m.boxRot(z.x,1.04f*scale+bob,z.z,0.73f*scale,1.00f*scale,0.42f*scale,shirt,z.yaw-0.10f);
            m.boxRot(z.x-rx*0.02f,1.72f*scale+bob,z.z-rz*0.02f,0.50f*scale,0.45f*scale,0.47f*scale,body,z.yaw);
            m.boxRot(z.x-rx*0.45f,1.17f*scale+bob+sw,z.z-rz*0.45f,0.26f*scale,0.85f*scale,0.25f*scale,body,z.yaw);
            m.boxRot(z.x+rx*0.45f,1.17f*scale+bob-sw,z.z+rz*0.45f,0.26f*scale,0.85f*scale,0.25f*scale,body,z.yaw);
            m.boxRot(z.x-rx*0.22f,0.39f*scale+sw*0.35f,z.z-rz*0.22f,0.27f*scale,0.72f*scale,0.30f*scale,0xff414541,z.yaw);
            m.boxRot(z.x+rx*0.22f,0.39f*scale-sw*0.35f,z.z+rz*0.22f,0.27f*scale,0.72f*scale,0.30f*scale,0xff414541,z.yaw);
            float eyeX=z.x+(float)Math.sin(z.yaw)*0.25f,eyeZ=z.z-(float)Math.cos(z.yaw)*0.25f;
            m.box(eyeX-rx*0.105f,1.78f*scale+bob,eyeZ+rz*0.02f,0.065f,0.07f,0.065f,z.type==4?0xff9ff29e:0xffdb6659);
            m.box(eyeX+rx*0.105f,1.78f*scale+bob,eyeZ-rz*0.02f,0.065f,0.07f,0.065f,z.type==4?0xff9ff29e:0xffdb6659);
            if(z.type==2||z.type==3) {
                m.boxRot(z.x,2.02f*scale+bob,z.z,0.62f,0.10f,0.6f,z.type==3?0xffa7afb0:0xff70594a,z.yaw);
            }
        }
        private int lootColor(int k) {
            switch(k){case 0:return 0xffdd5148;case 1:return 0xffe0bc68;case 2:return 0xff72a8d5;case 3:return 0xffc2c9bd;case 4:return 0xffd07c41;case 5:return 0xff6389b2;case 6:return 0xff7b9a6a;case 7:return 0xffb36e50;case 8:return 0xffe8d58d;case 9:return 0xff76b89b;default:return 0xffc5c5ad;}
        }
        private int shade(int co,float factor) {
            return Color.rgb(Math.max(0,Math.min(255,(int)(Color.red(co)*factor))),Math.max(0,Math.min(255,(int)(Color.green(co)*factor))),Math.max(0,Math.min(255,(int)(Color.blue(co)*factor))));
        }
        private static final String VERT =
            "attribute vec3 aPosition; attribute vec3 aColor; uniform mat4 uMvp; varying vec3 vColor; void main(){ gl_Position=uMvp*vec4(aPosition,1.0); vColor=aColor; }";
        private static final String FRAG =
            "precision mediump float; varying vec3 vColor; void main(){ gl_FragColor=vec4(vColor,1.0); }";
    }

    private static final class Mesh {
        FloatBuffer data;int count;
        Mesh(float[] raw,int used) {
            count=used/6;
            ByteBuffer bb=ByteBuffer.allocateDirect(used*4).order(ByteOrder.nativeOrder());
            data=bb.asFloatBuffer();data.put(raw,0,used);data.position(0);
        }
        void upload() { if(data!=null)data.position(0); }
    }

    private static final class MeshBuilder {
        float[] data;int n;FloatBuffer buffer;
        MeshBuilder(int initial){data=new float[Math.max(2048,initial)];}
        void reset(){n=0;}
        private void ensure(int add) {
            if(n+add>data.length)data=Arrays.copyOf(data,Math.max(data.length*2,n+add+4096));
        }
        private void vertex(float x,float y,float z,int color,float shade) {
            ensure(6);
            data[n++]=x;data[n++]=y;data[n++]=z;
            data[n++]=Math.max(0,Math.min(1,Color.red(color)/255f*shade));
            data[n++]=Math.max(0,Math.min(1,Color.green(color)/255f*shade));
            data[n++]=Math.max(0,Math.min(1,Color.blue(color)/255f*shade));
        }
        private void face(float[] v,int a,int b,int c,int d,int color,float sh) {
            vertex(v[a*3],v[a*3+1],v[a*3+2],color,sh);
            vertex(v[b*3],v[b*3+1],v[b*3+2],color,sh);
            vertex(v[c*3],v[c*3+1],v[c*3+2],color,sh);
            vertex(v[a*3],v[a*3+1],v[a*3+2],color,sh);
            vertex(v[c*3],v[c*3+1],v[c*3+2],color,sh);
            vertex(v[d*3],v[d*3+1],v[d*3+2],color,sh);
        }
        void box(float x,float y,float z,float w,float h,float d,int color){boxRot(x,y,z,w,h,d,color,0);}
        void boxRot(float x,float y,float z,float w,float h,float d,int color,float yaw) {
            float hw=w*.5f,hh=h*.5f,hd=d*.5f,cs=(float)Math.cos(yaw),sn=(float)Math.sin(yaw);
            float[] q={-hw,-hh,-hd, hw,-hh,-hd, hw,hh,-hd, -hw,hh,-hd,
                       -hw,-hh,hd, hw,-hh,hd, hw,hh,hd, -hw,hh,hd};
            float[] v=new float[24];
            for(int i=0;i<8;i++) {
                float lx=q[i*3],ly=q[i*3+1],lz=q[i*3+2];
                v[i*3]=x+lx*cs+lz*sn;v[i*3+1]=y+ly;v[i*3+2]=z+lx*sn-lz*cs;
            }
            face(v,0,1,2,3,color,0.78f);
            face(v,4,7,6,5,color,0.92f);
            face(v,0,4,5,1,color,0.72f);
            face(v,3,2,6,7,color,1.00f);
            face(v,1,5,6,2,color,0.86f);
            face(v,0,3,7,4,color,0.66f);
        }
        Mesh finish() {
            if(buffer==null || buffer.capacity()<n)buffer=ByteBuffer.allocateDirect(Math.max(n,4096)*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            buffer.clear();buffer.put(data,0,n);buffer.position(0);
            Mesh m=new Mesh(new float[0],0);m.data=buffer;m.count=n/6;return m;
        }
    }

    private static final class Matrix4 {
        void perspective(float[] m,float fovy,float aspect,float near,float far) {
            Arrays.fill(m,0);float f=1f/(float)Math.tan(Math.toRadians(fovy)/2);
            m[0]=f/aspect;m[5]=f;m[10]=(far+near)/(near-far);m[11]=-1;m[14]=(2*far*near)/(near-far);
        }
        void lookAt(float[] m,float ex,float ey,float ez,float cx,float cy,float cz,float ux,float uy,float uz) {
            float fx=cx-ex,fy=cy-ey,fz=cz-ez;float fl=(float)Math.sqrt(fx*fx+fy*fy+fz*fz);fx/=fl;fy/=fl;fz/=fl;
            float sx=fy*uz-fz*uy,sy=fz*ux-fx*uz,sz=fx*uy-fy*ux;float sl=(float)Math.sqrt(sx*sx+sy*sy+sz*sz);sx/=sl;sy/=sl;sz/=sl;
            float vx=sy*fz-sz*fy,vy=sz*fx-sx*fz,vz=sx*fy-sy*fx;
            m[0]=sx;m[1]=vx;m[2]=-fx;m[3]=0;
            m[4]=sy;m[5]=vy;m[6]=-fy;m[7]=0;
            m[8]=sz;m[9]=vz;m[10]=-fz;m[11]=0;
            m[12]=-(sx*ex+sy*ey+sz*ez);m[13]=-(vx*ex+vy*ey+vz*ez);m[14]=fx*ex+fy*ey+fz*ez;m[15]=1;
        }
        void multiply(float[] out,float[] a,float[] b) {
            float[] tmp=new float[16];
            for(int col=0;col<4;col++)for(int row=0;row<4;row++)tmp[col*4+row]=a[row]*b[col*4]+a[4+row]*b[col*4+1]+a[8+row]*b[col*4+2]+a[12+row]*b[col*4+3];
            System.arraycopy(tmp,0,out,0,16);
        }
    }
}
