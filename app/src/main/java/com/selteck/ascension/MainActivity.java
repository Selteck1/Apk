package com.selteck.ascension;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import java.util.Random;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(9, 10, 22));
        getWindow().setNavigationBarColor(Color.rgb(9, 10, 22));
        setContentView(new GameView());
    }

    final class GameView extends View {
        Canvas c; Paint p = new Paint(3); float sx, sy;
        SharedPreferences prefs;
        int level, xp, gold, hp, maxHp, attack, defense, kills, tab = 0;
        String enemy = "VOID STALKER";
        int enemyHp = 90, enemyMax = 90, enemyTier = 1;
        boolean flash = false;
        Random random = new Random();
        GameView() {
            super(MainActivity.this);
            prefs = getSharedPreferences("ascension_save", MODE_PRIVATE);
            level = prefs.getInt("level", 1); xp = prefs.getInt("xp", 0);
            gold = prefs.getInt("gold", 120); hp = prefs.getInt("hp", 120);
            maxHp = prefs.getInt("maxHp", 120); attack = prefs.getInt("attack", 18);
            defense = prefs.getInt("defense", 5); kills = prefs.getInt("kills", 0);
            if (hp < 1) hp = maxHp;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        void save() {
            prefs.edit().putInt("level",level).putInt("xp",xp).putInt("gold",gold)
                .putInt("hp",hp).putInt("maxHp",maxHp).putInt("attack",attack)
                .putInt("defense",defense).putInt("kills",kills).apply();
        }
        int col(String hex) { return Color.parseColor(hex); }
        void paint(int color) { p.reset(); p.setAntiAlias(true); p.setColor(color); }
        void rect(float l,float t,float r,float b,int color,float rad) {
            paint(color); c.drawRoundRect(l,t,r,b,rad,rad,p);
        }
        void grad(float l,float t,float r,float b,int a,int z,float rad) {
            paint(Color.WHITE); p.setShader(new LinearGradient(l,t,r,b,a,z,Shader.TileMode.CLAMP));
            c.drawRoundRect(l,t,r,b,rad,rad,p); p.setShader(null);
        }
        void text(String s,float x,float y,float size,int color,boolean bold) {
            paint(color); p.setTextSize(size); p.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));
            c.drawText(s,x,y,p);
        }
        void centered(String s,float x,float y,float size,int color,boolean bold) {
            paint(color); p.setTextSize(size); p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
            c.drawText(s,x-p.measureText(s)/2,y,p);
        }
        void bar(float x,float y,float w,float h,float pct,int bg,int fg) {
            rect(x,y,x+w,y+h,bg,h/2); rect(x,y,x+Math.max(h,w*Math.max(0,Math.min(1,pct))),y+h,fg,h/2);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); c=canvas;
            sx=getWidth()/390f; sy=getHeight()/844f;
            c.save(); c.scale(sx,sy);
            paint(Color.BLACK); c.drawRect(0,0,390,844,p);
            p.setShader(new LinearGradient(0,0,390,844,col("#10142C"),col("#080912"),Shader.TileMode.CLAMP));
            c.drawRect(0,0,390,844,p); p.setShader(null);
            // atmospheric glows and stars
            paint(col("#24235A")); p.setMaskFilter(new BlurMaskFilter(55,BlurMaskFilter.Blur.NORMAL)); c.drawCircle(305,210,92,p); p.setMaskFilter(null);
            paint(col("#5D2E8A")); p.setMaskFilter(new BlurMaskFilter(48,BlurMaskFilter.Blur.NORMAL)); c.drawCircle(65,420,62,p); p.setMaskFilter(null);
            for(int i=0;i<38;i++){ float x=(i*73+19)%390, y=(i*131+31)%520; paint(Color.argb(100+(i%4)*35,180,190,255)); c.drawCircle(x,y,0.7f+(i%3)*0.45f,p); }
            header();
            if(tab==0) battle(); else if(tab==1) hero(); else if(tab==2) inventory(); else world();
            bottomNav();
            c.restore();
        }
        void header() {
            text("A S C E N S I O N",20,34,11,col("#9D9DCC"),true);
            text("✦",344,35,18,col("#E6C77A"),true);
            text(" "+gold,364,34,13,col("#F5D98A"),true);
            text("THE SHATTERED REALM",20,63,21,Color.WHITE,true);
            text("SECTOR 01  •  THE VEIL",20,82,10,col("#7D80A8"),true);
            rect(20,98,370,156,col("#191B35"),15);
            grad(20,98,25,156,col("#D3A75E"),col("#7A4CC2"),2);
            // hero avatar
            paint(col("#24294D")); c.drawCircle(54,127,18,p);
            paint(col("#9A72F5")); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); c.drawCircle(54,127,18,p); p.setStyle(Paint.Style.FILL);
            centered("✦",54,134,20,col("#F4D48C"),true);
            text("VOIDWALKER",82,119,12,col("#F1F0FF"),true);
            text("LEVEL "+level+"  •  WANDERER",82,136,9,col("#A5A6CC"),true);
            bar(82,143,190,5,xp/(float)xpNeed(),col("#353653"),col("#B08BFF"));
            text("XP "+xp+" / "+xpNeed(),280,149,9,col("#C9B4FF"),true);
        }
        int xpNeed(){ return 80+level*35; }
        void battle() {
            text("CURRENT HUNT",20,183,10,col("#8589B7"),true);
            // enemy showcase arena
            rect(20,196,370,424,col("#101327"),18);
            p.setShader(new LinearGradient(20,200,350,420,col("#25234D"),col("#111426"),Shader.TileMode.CLAMP));
            c.drawRoundRect(20,196,370,424,18,18,p); p.setShader(null);
            // moon
            paint(col("#4B4D79")); c.drawCircle(310,240,29,p);
            paint(col("#7776A5")); c.drawCircle(302,232,22,p);
            // distant ruined towers
            paint(col("#17182F")); Path ruins=new Path(); ruins.moveTo(20,352);ruins.lineTo(56,300);ruins.lineTo(65,328);ruins.lineTo(86,286);ruins.lineTo(105,352);ruins.lineTo(137,322);ruins.lineTo(159,354);ruins.lineTo(190,315);ruins.lineTo(218,353);ruins.lineTo(249,330);ruins.lineTo(278,354);ruins.lineTo(315,302);ruins.lineTo(342,354);ruins.lineTo(370,330);ruins.lineTo(370,424);ruins.lineTo(20,424);ruins.close();c.drawPath(ruins,p);
            // glowing ground
            paint(col("#7046D2")); p.setMaskFilter(new BlurMaskFilter(18,BlurMaskFilter.Blur.NORMAL)); c.drawOval(88,366,298,397,p); p.setMaskFilter(null);
            paint(col("#382B72")); c.drawOval(78,372,308,401,p);
            // stylized shadow monster
            paint(col("#080A16")); Path body=new Path();body.moveTo(170,364);body.lineTo(153,323);body.lineTo(166,290);body.lineTo(158,266);body.lineTo(183,280);body.lineTo(198,247);body.lineTo(215,279);body.lineTo(237,265);body.lineTo(229,302);body.lineTo(246,329);body.lineTo(228,366);body.close();c.drawPath(body,p);
            paint(col("#2B1B4D")); c.drawOval(164,292,233,365,p);
            paint(col("#C46CFF")); p.setMaskFilter(new BlurMaskFilter(7,BlurMaskFilter.Blur.NORMAL)); c.drawOval(177,305,190,311,p);c.drawOval(207,305,220,311,p);p.setMaskFilter(null);
            paint(col("#F5B7FF")); c.drawOval(179,306,188,310,p);c.drawOval(209,306,218,310,p);
            // player silhouette
            paint(col("#111629")); c.drawCircle(104,345,14,p); c.drawRoundRect(91,357,117,389,7,7,p);
            paint(col("#8D73F4")); p.setStrokeWidth(4); c.drawLine(113,367,137,335,p); c.drawLine(137,335,151,313,p);
            paint(col("#C5A4FF")); p.setStrokeWidth(2); c.drawLine(137,335,153,309,p);
            // enemy label
            text(enemy,34,218,12,Color.WHITE,true);
            text("ELITE • TIER "+enemyTier,34,233,9,col("#D9A5FF"),true);
            bar(34,240,135,6,enemyHp/(float)enemyMax,col("#44283D"),col("#F15E91"));
            text(enemyHp+" / "+enemyMax+" HP",34,260,9,col("#E4B3CA"),false);
            // battle info card
            rect(20,439,370,498,col("#17192F"),14);
            text("YOUR VITALS",34,459,9,col("#898CB5"),true);
            text("HP",34,478,10,col("#D7D8F3"),true);
            bar(62,469,178,10,hp/(float)maxHp,col("#3E233E"),col("#E85C87"));
            text(hp+"/"+maxHp,248,478,10,col("#F3C1D2"),true);
            text("⚔ "+attack,294,478,12,col("#E6C77A"),true);
            // action buttons
            grad(20,514,370,574,col("#9C62F2"),col("#6038BC"),13);
            centered("⚔  STRIKE",195,538,16,Color.WHITE,true);
            centered("Tap to attack • earn XP & gold",195,558,10,col("#E3D7FF"),false);
            rect(20,587,187,644,col("#20213C"),12);
            text("✦  POWER",34,610,12,col("#D4BBFF"),true);
            text("Upgrade attack",34,628,10,col("#9295BC"),false);
            text(" "+upgradeCost()+" G",123,628,10,col("#F3D98E"),true);
            rect(203,587,370,644,col("#20213C"),12);
            text("✚  RECOVER",217,610,12,col("#FFB5CD"),true);
            text("Restore 35 HP",217,628,10,col("#9295BC"),false);
            text("30 G",326,628,10,col("#F3D98E"),true);
            rect(20,659,370,714,col("#15172A"),12);
            text("HUNT RECORD",34,679,9,col("#8589B7"),true);
            text("Defeated: "+kills,34,701,13,Color.WHITE,true);
            text("Next rank: "+(level*3)+" hunts",206,701,11,col("#B4A0F1"),true);
        }
        int upgradeCost(){return 45+attack*4;}
        void hero() {
            text("CHARACTER",20,184,18,Color.WHITE,true);
            rect(20,201,370,340,col("#191B35"),16);
            grad(32,213,112,293,col("#332D68"),col("#17182E"),18);
            centered("✦",72,266,42,col("#D5B5FF"),true);
            text("VOIDWALKER",128,232,15,Color.WHITE,true);
            text("Level "+level+" • Realm hunter",128,252,11,col("#A5A6CC"),false);
            text("XP "+xp+" / "+xpNeed(),128,270,10,col("#D0B6FF"),true);
            bar(128,280,215,7,xp/(float)xpNeed(),col("#343450"),col("#B08BFF"));
            String[] labels={"MAX HEALTH","ATTACK POWER","DEFENSE","ELITE HUNTS"};
            int[] vals={maxHp,attack,defense,kills};
            for(int i=0;i<4;i++){int y=367+i*66;rect(20,y,370,y+54,col("#191B35"),12);text(labels[i],34,y+21,10,col("#9093BB"),true);text(""+vals[i],310,y+29,19,i==1?col("#E8C97D"):Color.WHITE,true);}
            rect(20,650,370,714,col("#24203E"),12);
            text("TRAINING",34,672,10,col("#C7A9FF"),true);
            text("Upgrade attack from the battle screen.",34,696,11,col("#B4B5D1"),false);
        }
        void inventory() {
            text("RELIC INVENTORY",20,184,18,Color.WHITE,true);
            text("Equipment discovered in the Veil",20,203,10,col("#898CB5"),false);
            String[] names={"Riftfang","Warden's Mantle","Amethyst Core","Traveler's Sigil"};
            String[] desc={"Rare blade • +8 attack","Armor • +20 max HP","Arcane relic • unknown power","Accessory • +2 defense"};
            String[] rarity={"RARE","UNCOMMON","EPIC","COMMON"};
            int[] colors={0xff6ea8ff,0xff7fe0bd,0xffc28cff,0xffaeb1cc};
            for(int i=0;i<4;i++){int y=224+i*112;rect(20,y,370,y+96,col("#191B35"),14);grad(31,y+11,91,y+71,colors[i],col("#24213D"),12);centered(new String[]{"⚔","⬟","✦","◇"}[i],61,y+49,25,Color.WHITE,true);text(names[i],104,y+27,13,Color.WHITE,true);text(desc[i],104,y+47,10,col("#A5A6CC"),false);text(rarity[i],104,y+69,9,colors[i],true);rect(287,y+29,355,y+63,col("#2B2C49"),9);centered(i==0?"EQUIPPED":"LOCKED",321,y+49,8,col("#D7D3F4"),true);}
        }
        void world() {
            text("THE SHATTERED REALM",20,184,18,Color.WHITE,true);
            text("Choose your next expedition",20,203,10,col("#898CB5"),false);
            String[] names={"01  THE VEIL","02  ASHEN HOLLOW","03  FROSTBOUND","04  THE ABYSS"};
            String[] desc={"Corrupted shadows • Recommended Lv. 1","Ash beasts • Recommended Lv. 5","Frost giants • Recommended Lv. 12","Ancient horror • Recommended Lv. 20"};
            int[] colors={0xff483d7a,0xff854c43,0xff3b6b87,0xff7b315c};
            for(int i=0;i<4;i++){int y=224+i*119;grad(20,y,370,y+103,colors[i],col("#17182E"),14);paint(Color.argb(90,0,0,0));c.drawCircle(324,y+48,32,p);paint(colors[i]);c.drawCircle(324,y+48,22,p);centered(new String[]{"✧","♨","❄","☠"}[i],324,y+55,22,Color.WHITE,true);text(names[i],34,y+29,13,Color.WHITE,true);text(desc[i],34,y+49,9,col("#D2CBEA"),false);rect(34,y+64,145,y+89,col("#25213F"),8);centered(i==0?"ENTER":"LOCKED",89,y+81,9,i==0?col("#D8C0FF"):col("#777A9B"),true);}
        }
        void bottomNav() {
            rect(0,735,390,844,col("#0C0E1C"),0);
            rect(20,747,370,748,col("#292B49"),1);
            String[] labels={"BATTLE","HERO","RELICS","WORLD"};
            String[] icons={"⚔","♙","◇","⌖"};
            for(int i=0;i<4;i++){float x=48+i*98;int color=tab==i?col("#C9A5FF"):col("#777B9F");if(tab==i)rect(x-28,757,x+28,812,col("#282342"),13);centered(icons[i],x,780,20,color,true);centered(labels[i],x,801,8,color,true);}
            centered("PROJECT: ASCENSION  •  OFFLINE RPG",195,829,8,col("#565A7D"),true);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            float x=e.getX()/sx,y=e.getY()/sy;
            if(y>=747){if(x<96)tab=0;else if(x<194)tab=1;else if(x<292)tab=2;else tab=3;}
            else if(tab==0) {
                if(y>=514&&y<=576) attackEnemy();
                else if(y>=587&&y<=644&&x<195) upgrade();
                else if(y>=587&&y<=644&&x>=195) heal();
            } else if(tab==3&&y>=224&&y<327) { tab=0; }
            invalidate(); return true;
        }
        void attackEnemy() {
            int damage=attack+random.nextInt(Math.max(3,attack/2+1));
            enemyHp-=damage;
            if(enemyHp<=0) {
                kills++; gold+=25+enemyTier*10; xp+=30+enemyTier*12;
                if(xp>=xpNeed()){xp-=xpNeed();level++;maxHp+=18;hp=maxHp;attack+=3;defense+=2;}
                enemyTier=1+(kills/3); enemyMax=90+(enemyTier-1)*45; enemyHp=enemyMax;
                enemy=new String[]{"VOID STALKER","RIFT HOUND","DUSK REVENANT","VEIL GUARDIAN"}[(kills)%4];
            } else {
                int hit=Math.max(4, 13+enemyTier*2-defense);
                hp-=hit;
                if(hp<=0){hp=Math.max(1,maxHp/2);gold=Math.max(0,gold-10);}
            }
            save();
        }
        void upgrade() {
            int cost=upgradeCost();
            if(gold>=cost){gold-=cost;attack+=4;save();}
        }
        void heal() {
            if(gold>=30&&hp<maxHp){gold-=30;hp=Math.min(maxHp,hp+35);save();}
        }
    }
}