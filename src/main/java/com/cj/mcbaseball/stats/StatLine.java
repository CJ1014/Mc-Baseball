package com.cj.mcbaseball.stats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public final class StatLine {
    public String name = "";
    public int team;
    public int ab;
    public int h;
    public int r;
    public int hr;
    public int rbi;
    public int bb;
    public int so;
    public int pitches;
    public int pk;
    public int pbb;
    public int ra;
    public int po;
    public int a;
    public int e;
    public int games;

    public void add(StatLine o) {
        this.ab = this.ab + o.ab;
        this.h = this.h + o.h;
        this.r = this.r + o.r;
        this.hr = this.hr + o.hr;
        this.rbi = this.rbi + o.rbi;
        this.bb = this.bb + o.bb;
        this.so = this.so + o.so;
        this.pitches = this.pitches + o.pitches;
        this.pk = this.pk + o.pk;
        this.pbb = this.pbb + o.pbb;
        this.ra = this.ra + o.ra;
        this.po = this.po + o.po;
        this.a = this.a + o.a;
        this.e = this.e + o.e;
        this.games = this.games + o.games;
    }

    public boolean pitched() {
        return this.pitches > 0;
    }

    private int[] arr() {
        return new int[]{
            this.team,
            this.ab,
            this.h,
            this.r,
            this.hr,
            this.rbi,
            this.bb,
            this.so,
            this.pitches,
            this.pk,
            this.pbb,
            this.ra,
            this.po,
            this.a,
            this.e,
            this.games
        };
    }

    private void fromArr(int[] v) {
        if (v.length >= 16) {
            this.team = v[0];
            this.ab = v[1];
            this.h = v[2];
            this.r = v[3];
            this.hr = v[4];
            this.rbi = v[5];
            this.bb = v[6];
            this.so = v[7];
            this.pitches = v[8];
            this.pk = v[9];
            this.pbb = v[10];
            this.ra = v[11];
            this.po = v[12];
            this.a = v[13];
            this.e = v[14];
            this.games = v[15];
        }
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("N", this.name);
        t.putIntArray("S", this.arr());
        return t;
    }

    public static StatLine load(CompoundTag t) {
        StatLine s = new StatLine();
        s.name = t.getString("N");
        s.fromArr(t.getIntArray("S"));
        return s;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.name, 64);
        buf.writeVarIntArray(this.arr());
    }

    public static StatLine read(FriendlyByteBuf buf) {
        StatLine s = new StatLine();
        s.name = buf.readUtf(64);
        s.fromArr(buf.readVarIntArray());
        return s;
    }
}
