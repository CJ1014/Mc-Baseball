package com.cj.mcbaseball.team;

import com.cj.mcbaseball.game.Position;
import com.cj.mcbaseball.pitching.PitchType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

public final class NpcProfile {
    public final String name;
    public final int number;
    public final boolean batsRight;
    public final boolean throwsRight;
    public final int contact;
    public final int power;
    public final int speed;
    public final int fielding;
    public final int arm;
    public final int eye;
    public final int velocity;
    public final int control;
    public final int stuff;
    public final int skin;
    public final EnumSet<PitchType> repertoire;

    public NpcProfile(
        String name,
        int number,
        boolean batsRight,
        boolean throwsRight,
        int contact,
        int power,
        int speed,
        int fielding,
        int arm,
        int eye,
        int velocity,
        int control,
        int stuff,
        int skin,
        EnumSet<PitchType> repertoire
    ) {
        this.name = name;
        this.number = number;
        this.batsRight = batsRight;
        this.throwsRight = throwsRight;
        this.contact = contact;
        this.power = power;
        this.speed = speed;
        this.fielding = fielding;
        this.arm = arm;
        this.eye = eye;
        this.velocity = velocity;
        this.control = control;
        this.stuff = stuff;
        this.skin = skin;
        this.repertoire = repertoire;
    }

    public String displayName() {
        return this.name + " #" + this.number;
    }

    public static NpcProfile generate(Random r, Position pos, List<Integer> usedNumbers) {
        String name = NameGenerator.random(r);

        int number;
        do {
            number = 1 + r.nextInt(99);
        } while (usedNumbers.contains(number));

        usedNumbers.add(number);
        int contact = roll(r, 62);
        int power = roll(r, 58);
        int speed = roll(r, 60);
        int fielding = roll(r, 62);
        int arm = roll(r, 60);
        int eye = roll(r, 58);
        int velocity = roll(r, 45);
        int control = roll(r, 45);
        int stuff = roll(r, 45);
        switch (pos) {
            case PITCHER:
                velocity = roll(r, 76);
                control = roll(r, 72);
                stuff = roll(r, 72);
                contact = roll(r, 42);
                power = roll(r, 38);
                break;
            case CATCHER:
                fielding = roll(r, 72);
                arm = roll(r, 74);
                speed = roll(r, 42);
                break;
            case FIRST_BASE:
                power = roll(r, 74);
                speed = roll(r, 46);
                break;
            case SHORTSTOP:
                fielding = roll(r, 76);
                arm = roll(r, 72);
                speed = roll(r, 68);
                break;
            case SECOND_BASE:
                fielding = roll(r, 72);
                contact = roll(r, 68);
                break;
            case THIRD_BASE:
                arm = roll(r, 74);
                power = roll(r, 66);
                break;
            case CENTER_FIELD:
                speed = roll(r, 78);
                fielding = roll(r, 70);
                break;
            case LEFT_FIELD:
                power = roll(r, 70);
                break;
            case RIGHT_FIELD:
                arm = roll(r, 78);
        }

        boolean batsRight = r.nextFloat() < 0.72F;
        boolean throwsRight = r.nextFloat() < 0.75F;
        EnumSet<PitchType> rep = EnumSet.of(PitchType.FOUR_SEAM);
        List<PitchType> extras = new ArrayList<>(List.of(PitchType.TWO_SEAM, PitchType.CHANGEUP, PitchType.CURVEBALL, PitchType.SLIDER, PitchType.SINKER));
        int extraCount = pos == Position.PITCHER ? 3 + r.nextInt(2) : 1 + r.nextInt(2);

        for (int i = 0; i < extraCount && !extras.isEmpty(); i++) {
            rep.add(extras.remove(r.nextInt(extras.size())));
        }

        return new NpcProfile(name, number, batsRight, throwsRight, contact, power, speed, fielding, arm, eye, velocity, control, stuff, r.nextInt(10), rep);
    }

    private static int roll(Random r, int mean) {
        return Mth.clamp((int)Math.round((double)mean + r.nextGaussian() * 11.0), 20, 99);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Name", this.name);
        t.putInt("Num", this.number);
        t.putBoolean("BR", this.batsRight);
        t.putBoolean("TR", this.throwsRight);
        t.putIntArray("R", new int[]{this.contact, this.power, this.speed, this.fielding, this.arm, this.eye, this.velocity, this.control, this.stuff, this.skin});
        int mask = 0;

        for (PitchType p : this.repertoire) {
            mask |= 1 << p.ordinal();
        }

        t.putInt("Rep", mask);
        return t;
    }

    public static NpcProfile load(CompoundTag t) {
        int[] r = t.getIntArray("R");
        if (r.length < 10) {
            r = new int[]{60, 60, 60, 60, 60, 60, 60, 60, 60, 0};
        }

        return new NpcProfile(
            t.getString("Name"),
            t.getInt("Num"),
            t.getBoolean("BR"),
            t.getBoolean("TR"),
            r[0],
            r[1],
            r[2],
            r[3],
            r[4],
            r[5],
            r[6],
            r[7],
            r[8],
            r[9],
            repFromMask(t.getInt("Rep"))
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(this.save());
    }

    public static NpcProfile read(FriendlyByteBuf buf) {
        CompoundTag t = buf.readNbt();
        return load(t == null ? new CompoundTag() : t);
    }

    private static EnumSet<PitchType> repFromMask(int mask) {
        EnumSet<PitchType> s = EnumSet.noneOf(PitchType.class);

        for (PitchType p : PitchType.values()) {
            if ((mask & 1 << p.ordinal()) != 0) {
                s.add(p);
            }
        }

        if (s.isEmpty()) {
            s.add(PitchType.FOUR_SEAM);
        }

        return s;
    }
}
