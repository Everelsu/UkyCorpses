package com.uky.corpses.corpse;

import java.util.Random;

import net.minecraft.util.DamageSource;

/**
 * Every way a body can lie. Per pose a list of variants, each tagged with the kind of death it fits (GENERIC fits any):
 * burnt bodies curl into the boxer's pose, the shot clutch their chest, the drowned float limp, the blown up are
 * flung wide. A body's look packs the variant, a mirror flag, a jitter seed (every body is a little different) and
 * the cause; the server picks it once and both sides read the same angles from it, so hitboxes match the drawing.
 *
 * Angles are ModelBiped's, radians, head / right arm / left arm / right leg / left leg as x y z. Checked against the
 * floor by Skeleton.lift: nothing floats, little sinks. The first four of LIE, SIT and PRONE are the old ones, so
 * bodies saved before keep their look.
 */
public final class Poses {

    /** Kinds of death, as far as the pose cares. */
    public static final int GENERIC = 0, FALL = 1, FIRE = 2, BLAST = 3, SHOT = 4, MELEE = 5, DROWN = 6, SICK = 7;
    /** EntityCorpse poses: on the back, against a wall, face down, on the side (right side down unless mirrored). */
    public static final int LIE = 0, SIT = 1, PRONE = 2, SIDE = 3;

    /** [pose][variant] = { cause, roll (degrees, SIDE only), 15 angles }. */
    private static final float[][][] TABLE = { { // LIE: on the back
        { GENERIC, 0, 0, .6F, 0, 0, 0, .4F, .1F, 0, -.25F, 0, 0, .1F, 0, 0, -.15F },
        { GENERIC, 0, 0, -.5F, .1F, -.05F, 0, .9F, -.1F, 0, -.8F, 0, 0, .25F, 0, 0, -.3F },
        { GENERIC, 0, .1F, .25F, 0, -2.8F, 0, .3F, 0, 0, -.35F, 0, 0, .05F, -.1F, 0, -.1F },
        { GENERIC, 0, -.05F, -.8F, 0, -.1F, 0, .2F, -.4F, 0, -.9F, -.1F, 0, .4F, -.1F, 0, -.05F },
        { GENERIC, 0, 0, .45F, 0, 0, 0, 1.35F, 0, 0, -1.4F, 0, .1F, .38F, 0, -.1F, -.32F }, // starfish
        { GENERIC, 0, -.1F, -.3F, 0, -3F, 0, -.25F, -3F, 0, .3F, 0, 0, .12F, 0, 0, -.1F }, // arms over the head
        { GENERIC, 0, 0, .7F, .05F, -.45F, 0, -.95F, 0, 0, -.5F, 0, .15F, .1F, -.05F, 0, -.2F }, // arm over the chest
        { GENERIC, 0, .05F, -.6F, 0, .1F, 0, .45F, -.5F, 0, .75F, 0, .2F, -.1F, 0, -.2F, .12F }, // ankles crossed
        { GENERIC, 0, -.15F, .2F, -.1F, 0, 0, .7F, -.1F, 0, -.65F, -.05F, 0, .2F, -.05F, 0, -.22F }, // head back
        { GENERIC, 0, .05F, -.9F, 0, -2.9F, 0, .15F, 0, 0, -1F, 0, .3F, .55F, 0, 0, -.05F }, // twisted
        { FIRE, 0, .35F, .2F, 0, -1.25F, 0, -.3F, -1.3F, 0, .25F, -.3F, 0, .1F, -.25F, 0, -.1F }, // fists up
        { FIRE, 0, .3F, -.3F, 0, -1.05F, 0, -.45F, -1.15F, 0, .35F, -.2F, 0, .05F, -.35F, 0, -.1F },
        { SHOT, 0, .25F, .5F, 0, -.8F, 0, -.5F, -.75F, 0, .45F, 0, 0, .15F, 0, 0, -.15F }, // hands on the chest
        { SHOT, 0, 0, -.7F, 0, -.6F, 0, -.6F, 0, 0, -.9F, 0, 0, .2F, -.05F, 0, -.1F },
        { DROWN, 0, -.05F, .3F, 0, -.3F, 0, .9F, -.3F, 0, -.95F, -.1F, 0, .3F, -.1F, 0, -.3F }, // limp, arms afloat
        { BLAST, 0, -.1F, .9F, 0, -2.6F, 0, .65F, 0, 0, -1.45F, 0, .2F, .55F, 0, -.2F, -.5F }, // flung wide
        { BLAST, 0, .1F, -1F, 0, 0, 0, 1.45F, -2.4F, 0, -.5F, -.05F, 0, .45F, 0, 0, -.15F },
        { FALL, 0, -.1F, .8F, 0, -2.2F, 0, -.25F, 0, 0, -1.1F, 0, .4F, .5F, 0, 0, -.05F },
        { MELEE, 0, .1F, -.7F, 0, -2.5F, 0, -.6F, 0, 0, -.6F, 0, 0, .2F, 0, 0, -.2F }, // arm over the face
        { MELEE, 0, 0, .75F, 0, 0, 0, 1F, -.9F, 0, .6F, 0, 0, .1F, 0, .3F, -.4F },
        { SICK, 0, .1F, .4F, 0, -.3F, 0, -.2F, -.35F, 0, .3F, -.15F, 0, .05F, -.1F, 0, -.05F }, // hands folded
        { GENERIC, 0, .1F, .95F, 0, 0, 0, .3F, -.5F, 0, .7F, 0, 0, .15F, 0, 0, -.2F }, // a hand on the belly
        { GENERIC, 0, .15F, -.4F, 0, -.6F, 0, -.7F, -.6F, 0, .7F, 0, .1F, .05F, 0, -.1F, -.05F }, // laid out
        { GENERIC, 0, -.05F, .6F, 0, -2.9F, 0, .1F, 0, 0, -1.2F, 0, 0, .3F, 0, 0, -.45F }, // one arm up, one out
        { GENERIC, 0, .05F, -.85F, 0, 0, 0, 1.1F, 0, 0, -.2F, 0, -.15F, .45F, .15F, 0, -.1F }, // arm and leg out
        { GENERIC, 0, .2F, .2F, 0, 0, 0, .15F, 0, 0, -.15F, 0, .2F, .02F, -.2F, 0, -.02F }, // straight, toes out
        { GENERIC, 0, -.1F, -.55F, .05F, -.5F, 0, -.9F, 0, 0, -1.25F, 0, 0, .5F, 0, 0, -.2F },
        { FALL, 0, -.2F, .9F, 0, 0, 0, 1.2F, -.3F, 0, -1.3F, 0, .3F, .55F, 0, -.3F, -.55F }, // splayed
        { FALL, 0, -.15F, -.6F, .1F, -2.7F, 0, .9F, 0, 0, -.8F, 0, 0, .5F, .2F, 0, -.05F },
        { SHOT, 0, .2F, .7F, 0, -.6F, 0, -.6F, 0, 0, -1F, 0, 0, .25F, 0, 0, -.15F }, // a hand on the wound
        { MELEE, 0, .05F, .9F, 0, -2.3F, 0, -.7F, -2.3F, 0, .7F, 0, 0, .2F, 0, 0, -.2F }, // arms up to the face
        { DROWN, 0, -.1F, -.4F, 0, -2.8F, 0, .5F, -2.7F, 0, -.6F, 0, 0, .4F, 0, 0, -.4F }, // arms up, adrift
        { SICK, 0, .25F, -.5F, 0, -.6F, 0, -.55F, 0, 0, -.35F, 0, 0, .05F, 0, 0, -.1F }, // holding the stomach
        { BLAST, 0, -.2F, -.9F, .1F, -2.9F, 0, 1.2F, .2F, 0, -1.4F, 0, -.2F, .6F, .2F, 0, -.3F },
        { FIRE, 0, .4F, -.2F, 0, -.9F, 0, -.6F, -.95F, 0, .6F, 0, 0, .15F, 0, 0, -.15F }, // arms drawn in
        { GENERIC, 0, -.1F, .3F, 0, -1.55F, .1F, .05F, 0, 0, -.3F, 0, 0, .15F, 0, 0, -.2F }, // a fist to the sky
        { GENERIC, 0, -.15F, 0, 0, -1.5F, .15F, .1F, -1.5F, -.15F, -.1F, 0, 0, .1F, 0, 0, -.1F }, // reaching up, both
        { GENERIC, 0, .1F, 0, 0, -.45F, 0, -.95F, -.4F, 0, .95F, 0, 0, .05F, 0, 0, -.05F }, // pharaoh, arms crossed
        { GENERIC, 0, 0, .1F, 0, 0, 0, 1.55F, 0, 0, -1.55F, 0, 0, .05F, 0, 0, -.05F }, // T
        { GENERIC, 0, -.1F, 0, 0, -2.75F, 0, .55F, -2.75F, 0, -.55F, 0, 0, .3F, 0, 0, -.3F }, // Y, arms up and out
        { GENERIC, 0, .05F, -.5F, 0, -1.1F, 0, -1.1F, -2.3F, 0, -.9F, 0, 0, .2F, 0, 0, -.15F }, // the dab
        { GENERIC, 0, .1F, .4F, 0, -2.3F, 0, -.55F, 0, 0, -.2F, 0, .25F, .4F, 0, 0, -.1F }, // a hand to the brow
        { GENERIC, 0, 0, -.6F, 0, -1.55F, 0, .35F, -.2F, 0, -.9F, 0, .15F, .5F, 0, -.15F, -.5F }, // pointing up, legs
                                                                                                  // wide
        { FALL, 0, -.2F, .2F, 0, -1.4F, -.2F, 1F, -1.4F, .2F, -1F, 0, .2F, .6F, 0, -.2F, -.6F }, // still bracing
        { MELEE, 0, .1F, .2F, 0, -1.35F, 0, -.45F, -1.35F, 0, .45F, 0, 0, .15F, 0, 0, -.15F }, // fending off
        { SHOT, 0, .15F, -.3F, 0, -1.55F, 0, -.2F, -.3F, 0, .5F, 0, 0, .2F, 0, 0, -.1F }, // a hand up, too late
        { SICK, 0, .2F, .6F, 0, -.3F, 0, -1.2F, -.35F, 0, 1.2F, 0, .2F, -.1F, 0, -.2F, .1F }, // arms wrapped round
        }, { // SIT: slumped against the wall, legs out
            { GENERIC, 0, .6F, 0, .25F, -.25F, 0, .15F, -.35F, 0, -.1F, -1.4F, .3F, 0, -1.4F, -.25F, 0 },
            { GENERIC, 0, .4F, -.5F, -.2F, -.9F, 0, .05F, -.8F, 0, -.05F, -1.5F, .15F, .05F, -1.35F, -.35F, 0 },
            { GENERIC, 0, -.1F, 0, .3F, -.1F, 0, .5F, -.2F, 0, -.5F, -1.3F, .5F, 0, -1.5F, -.5F, 0 },
            { GENERIC, 0, .9F, .2F, 0, -.3F, 0, .1F, -.15F, 0, -.1F, -1.45F, .1F, 0, -1.4F, -.1F, 0 },
            { GENERIC, 0, .55F, 0, -.5F, -.55F, 0, -.2F, -.5F, 0, .2F, -1.45F, .2F, 0, -1.4F, -.2F, 0 }, // hands in lap
            { GENERIC, 0, .45F, .3F, .2F, -.2F, 0, .35F, -.25F, 0, -.35F, -1.2F, .1F, 0, -1.5F, -.3F, 0 }, // knee up
            { GENERIC, 0, -.25F, .35F, 0, 0, 0, .25F, 0, 0, -.25F, -1.45F, .25F, .05F, -1.45F, -.25F, -.05F },
            { GENERIC, 0, .7F, -.4F, .15F, -.4F, 0, .4F, -.45F, 0, -.15F, -1.45F, .15F, -.12F, -1.45F, -.1F, .12F },
            { SHOT, 0, .75F, 0, 0, -.95F, 0, -.5F, -.9F, 0, .5F, -1.4F, .15F, 0, -1.4F, -.15F, 0 }, // clutching
            { MELEE, 0, .8F, .3F, -.2F, -.6F, 0, -.3F, 0, 0, -.3F, -1.3F, .3F, 0, -1.5F, -.1F, 0 },
            { FIRE, 0, .6F, 0, 0, -1.3F, 0, -.35F, -1.3F, 0, .35F, -1.3F, .1F, 0, -1.32F, -.1F, 0 }, // curled
            { SICK, 0, 1F, 0, .1F, -.7F, 0, -.4F, -.65F, 0, .4F, -1.32F, .1F, 0, -1.35F, -.1F, 0 },
            { GENERIC, 0, .85F, .5F, .1F, -.15F, 0, .3F, -.2F, 0, -.3F, -1.45F, .2F, .1F, -1.4F, -.2F, -.1F }, // lolling
            { GENERIC, 0, .3F, -.7F, -.2F, -.2F, 0, .2F, -.6F, 0, -.1F, -1.35F, .35F, 0, -1.45F, -.1F, 0 },
            { GENERIC, 0, .65F, 0, .35F, -.05F, 0, .5F, -.05F, 0, -.5F, -1.4F, .45F, .05F, -1.4F, -.45F, -.05F }, // arms
                                                                                                                  // dropped
            { GENERIC, 0, .5F, .2F, 0, -.7F, 0, -.3F, -.15F, 0, .1F, -1.5F, 0, 0, -1.3F, -.4F, 0 }, // a hand in the lap
            { FALL, 0, -.2F, -.4F, .2F, 0, 0, .6F, -.1F, 0, -.6F, -1.3F, .5F, .1F, -1.5F, -.3F, 0 },
            { BLAST, 0, .2F, .6F, -.3F, -.2F, 0, .7F, 0, 0, -.4F, -1.25F, .4F, 0, -1.45F, -.4F, 0 },
            { DROWN, 0, .7F, -.2F, 0, -.1F, 0, .1F, -.1F, 0, -.1F, -1.4F, .1F, 0, -1.4F, -.1F, 0 },
            { SHOT, 0, .9F, -.3F, 0, -.8F, 0, -.6F, -.3F, 0, .2F, -1.35F, .3F, 0, -1.45F, -.2F, 0 }, // a hand on the
                                                                                                     // chest
            { GENERIC, 0, .2F, .2F, 0, -2.9F, .1F, .2F, -.2F, 0, -.2F, -1.4F, .2F, 0, -1.45F, -.2F, 0 }, // a hand up:
                                                                                                         // here!
            { GENERIC, 0, .55F, -.3F, 0, -2.2F, 0, -.55F, -.5F, 0, .1F, -1.4F, .2F, 0, -1.4F, -.2F, 0 }, // facepalm
            { GENERIC, 0, .3F, 0, 0, -.6F, 0, .9F, -.6F, 0, -.9F, -1.45F, .25F, 0, -1.45F, -.25F, 0 }, // shrugging
            { GENERIC, 0, .1F, .4F, 0, -1.55F, 0, .1F, -.15F, 0, -.3F, -1.35F, .3F, 0, -1.5F, -.2F, 0 }, // pointing
                                                                                                         // ahead
            { GENERIC, 0, .45F, 0, 0, -.9F, 0, -.8F, -.9F, 0, .8F, -1.4F, .15F, 0, -1.4F, -.15F, 0 }, // arms folded
            { GENERIC, 0, -.3F, .3F, 0, -2.7F, 0, -.35F, -2.7F, 0, .35F, -1.4F, .3F, 0, -1.4F, -.3F, 0 }, // hands
                                                                                                          // behind the
                                                                                                          // head
            { MELEE, 0, .5F, 0, 0, -1.9F, 0, -.3F, -1.9F, 0, .3F, -1.3F, .2F, 0, -1.4F, -.2F, 0 }, // guard still up
            { FIRE, 0, .7F, 0, 0, -1.6F, 0, -.6F, -1.6F, 0, .6F, -1.6F, .15F, 0, -1.6F, -.15F, 0 }, // knees and arms in
        }, { // PRONE: face down, cheek on the floor
            { GENERIC, 0, -.3F, 1.2F, 0, -3.2F, 0, .25F, .1F, 0, -.15F, 0, 0, .1F, 0, 0, -.1F },
            { GENERIC, 0, -.3F, -1.2F, 0, .1F, 0, .15F, .15F, 0, -.15F, 0, 0, .15F, 0, 0, -.1F },
            { GENERIC, 0, -.4F, .9F, 0, -3.25F, 0, .6F, .3F, 0, -.2F, .15F, 0, .2F, .05F, 0, -.2F },
            { GENERIC, 0, -.3F, 1.2F, 0, -3.25F, 0, .5F, -3.25F, 0, -.5F, 0, 0, .3F, 0, 0, -.3F },
            { GENERIC, 0, -.2F, -1.25F, 0, 0, 0, 1.35F, 0, 0, -1.35F, .05F, 0, .25F, .05F, 0, -.25F }, // arms wide
            { GENERIC, 0, -.3F, 1.25F, 0, .15F, 0, .12F, .15F, 0, -.12F, .1F, .2F, .55F, 0, 0, -.08F }, // frog leg
            { GENERIC, 0, -.25F, -1.1F, 0, -3.25F, 0, .2F, -3.25F, 0, -.2F, .05F, 0, .1F, .05F, 0, -.1F }, // reaching
            { GENERIC, 0, -.35F, 1.2F, 0, .2F, 0, .05F, -3.25F, 0, -.35F, 0, 0, .18F, .1F, 0, -.3F },
            { SHOT, 0, -.3F, -1.2F, 0, -3.25F, 0, .35F, .25F, 0, -.1F, .1F, 0, .12F, .1F, 0, -.15F }, // shot running
            { MELEE, 0, -.35F, 1.15F, 0, -3.3F, 0, -.45F, -3.3F, 0, .45F, 0, 0, .2F, 0, 0, -.2F }, // covering the head
            { FALL, 0, -.25F, -1.2F, 0, .05F, 0, 1.2F, -3.3F, 0, -.7F, .2F, .2F, .6F, 0, 0, -.3F },
            { DROWN, 0, -.4F, .6F, 0, -3.3F, 0, .9F, -3.3F, 0, -.9F, .2F, 0, .25F, .2F, 0, -.25F },
            { BLAST, 0, -.3F, 1.3F, 0, -3.3F, 0, .9F, .05F, 0, -1.45F, .1F, .3F, .6F, .05F, 0, -.4F },
            { SICK, 0, -.3F, 1.2F, 0, .1F, 0, -.05F, .1F, 0, .05F, .05F, 0, .05F, .05F, 0, -.05F },
            { GENERIC, 0, -.3F, -1.2F, 0, .1F, 0, .3F, .1F, 0, -.3F, .1F, 0, .35F, .1F, 0, -.35F }, // legs apart
            { GENERIC, 0, -.35F, 1.25F, 0, -3.25F, 0, .4F, .15F, 0, -.6F, .05F, 0, .15F, .1F, 0, -.05F },
            { GENERIC, 0, -.25F, -1.15F, 0, .2F, 0, .8F, -3.2F, 0, .1F, .15F, .1F, .2F, 0, 0, -.25F },
            { GENERIC, 0, -.3F, 1.2F, 0, -3.3F, 0, .15F, -3.3F, 0, -.15F, .1F, .2F, .3F, .1F, -.2F, -.3F }, // diving
            { GENERIC, 0, -.35F, -1.25F, 0, .1F, 0, -.1F, .1F, 0, .1F, .05F, 0, .05F, .05F, 0, -.05F }, // straight
            { FALL, 0, -.2F, 1.2F, 0, -3.2F, 0, 1F, -3.2F, 0, -1F, .15F, .1F, .55F, .15F, -.1F, -.55F }, // spread-eagle
            { SHOT, 0, -.3F, 1.2F, 0, .2F, 0, .5F, .05F, 0, -.1F, .2F, .3F, .2F, 0, 0, -.1F }, // fell running
            { MELEE, 0, -.3F, -1.2F, 0, -3.25F, 0, .5F, .15F, 0, -.2F, .1F, 0, .25F, .05F, 0, -.15F },
            { DROWN, 0, -.35F, -.7F, 0, -3.3F, 0, .7F, -3.3F, 0, -.7F, .25F, 0, .3F, .25F, 0, -.3F },
            { FIRE, 0, -.3F, 1.15F, 0, -2.9F, 0, -.2F, .3F, 0, .3F, .2F, 0, .1F, .2F, 0, -.1F },
            { BLAST, 0, -.25F, -1.25F, 0, -3.25F, 0, 1.2F, .05F, 0, -1.3F, .15F, -.2F, .55F, .05F, 0, -.2F },
            { GENERIC, 0, -.3F, 1.2F, 0, .55F, 0, -.35F, .55F, 0, .35F, .05F, 0, .1F, .05F, 0, -.1F }, // hands behind
                                                                                                       // the back
            { GENERIC, 0, -.3F, -1.2F, 0, -3.25F, 0, .1F, .1F, 0, -1.5F, .05F, 0, .15F, .05F, 0, -.15F }, // reaching
                                                                                                          // for it
            { GENERIC, 0, -.25F, 1.2F, 0, 0, 0, 1.5F, 0, 0, -1.5F, .1F, 0, .35F, .1F, 0, -.35F }, // T, face down
            { GENERIC, 0, -.3F, -1.15F, 0, -3.3F, 0, .2F, -3.3F, 0, -.2F, .2F, 0, .1F, .2F, 0, -.1F }, // superman
            { FALL, 0, -.25F, 1.2F, 0, -1.6F, 0, 1.3F, -1.6F, 0, -1.3F, .15F, .2F, .5F, .15F, -.2F, -.5F }, // breaking
                                                                                                            // the fall
            { BLAST, 0, -.3F, -1.25F, 0, .5F, 0, 1.1F, -3.3F, 0, -.9F, .2F, .3F, .6F, .1F, 0, -.4F } }, // one arm back
        { // SIDE: on the right side (mirrored: the left); the lower arm comes out in front, the upper one drapes
            { GENERIC, 88F, .35F, 0, 0, -1.2F, -.55F, 0, -.9F, .7F, 0, -.8F, 0, 0, -1.1F, 0, 0 }, // fetal
            { GENERIC, 92F, .1F, 0, 0, -1.5F, -.45F, 0, -.7F, .65F, 0, 0, 0, 0, -1F, 0, 0 }, // recovery position
            { GENERIC, 85F, -.1F, 0, 0, -2.8F, -.2F, 0, -.25F, .3F, 0, -.3F, 0, 0, .2F, 0, 0 }, // reaching on
            { GENERIC, 95F, .15F, .2F, 0, -1.4F, -.5F, 0, .45F, -.45F, 0, .35F, 0, 0, -.6F, 0, 0 }, // scissored
            { GENERIC, 80F, .05F, 0, 0, -.6F, -.6F, 0, -1.1F, .75F, 0, -.45F, 0, 0, -.55F, 0, 0 },
            { FIRE, 90F, .55F, 0, 0, -1.35F, -.5F, 0, -1.4F, .45F, 0, -1.2F, 0, 0, -1.3F, 0, 0 }, // curled tight
            { SHOT, 90F, .45F, 0, 0, -.9F, -.6F, 0, -.95F, .9F, 0, -.5F, 0, 0, -.75F, 0, 0 }, // holding the belly
            { BLAST, 100F, -.15F, 0, 0, -2.5F, -.3F, 0, .6F, -.5F, 0, .5F, 0, 0, -.4F, 0, 0 },
            { SICK, 88F, .5F, 0, 0, -1.1F, -.55F, 0, -1F, .8F, 0, -1F, 0, 0, -1.05F, 0, 0 },
            { MELEE, 85F, .3F, 0, 0, -2.4F, -.3F, 0, -2.2F, .5F, 0, -.6F, 0, 0, -.3F, 0, 0 },
            { GENERIC, 90F, .2F, .1F, 0, -1.6F, -.4F, 0, -.5F, .5F, 0, -.2F, 0, 0, -.9F, 0, 0 }, // top leg forward
            { GENERIC, 86F, .25F, -.15F, 0, -.9F, -.5F, 0, -1.3F, .6F, 0, -.9F, 0, 0, -.4F, 0, 0 },
            { GENERIC, 94F, 0, 0, 0, -2.6F, -.25F, 0, -.3F, .4F, 0, -.15F, 0, 0, -.25F, 0, 0 }, // stretched out
            { GENERIC, 90F, .4F, .1F, 0, -1.3F, -.6F, 0, -1.2F, .75F, 0, -1.3F, 0, 0, -1.35F, 0, 0 }, // curled up
            { FALL, 98F, -.1F, 0, 0, -2F, -.3F, 0, .3F, -.3F, 0, .2F, 0, 0, -.7F, 0, 0 },
            { DROWN, 92F, .1F, 0, 0, -1.8F, -.3F, 0, -1.6F, .3F, 0, -.3F, 0, 0, -.6F, 0, 0 },
            { MELEE, 88F, .45F, 0, 0, -2.6F, -.5F, 0, -2.4F, .6F, 0, -.9F, 0, 0, -1F, 0, 0 }, // arms over the head
            { BLAST, 105F, -.2F, .2F, 0, -2.8F, -.2F, 0, .5F, -.4F, 0, .6F, 0, 0, -.8F, 0, 0 },
            { GENERIC, 90F, .2F, 0, 0, -1.55F, -.4F, 0, -2.8F, .3F, 0, -.6F, 0, 0, -.8F, 0, 0 }, // waving the last
            { GENERIC, 88F, .25F, 0, 0, -1.55F, -.5F, 0, -1.55F, .6F, 0, -.5F, 0, 0, -.5F, 0, 0 }, // both hands out
                                                                                                   // ahead
            { GENERIC, 92F, -.2F, 0, 0, -2.9F, -.2F, 0, -2.9F, .2F, 0, -.2F, 0, 0, -.1F, 0, 0 }, // stretched, arms over
            { SHOT, 90F, .4F, 0, 0, -1.55F, -.5F, 0, -.6F, .9F, 0, -.6F, 0, 0, -.9F, 0, 0 } } }; // reaching, holding
                                                                                                 // the wound

    /** Most cause-fitting deaths take a variant made for them; the rest any generic one. */
    private static final float FITTING = 0.7F;
    /**
     * Jitter amplitude per angle: the head turns freely; arms and legs barely swing toward the floor (they'd lift the
     * body off it), more sideways.
     */
    private static final float[] JITTER = { .12F, .25F, .1F, .05F, .12F, .12F, .05F, .12F, .12F, .05F, .08F, .06F, .05F,
        .08F, .06F };

    private Poses() {}

    public static int count(int pose) {
        return TABLE[clamp(pose)].length;
    }

    private static int clamp(int pose) {
        return pose < 0 || pose > SIDE ? LIE : pose;
    }

    /** What kind of death this was, for the pose. */
    public static int cause(DamageSource s, boolean fell, boolean burnt) {
        if (s == null) return GENERIC;
        String t = s.damageType == null ? "" : s.damageType;
        if (s.isExplosion() || t.startsWith("explosion") || t.equals("fireball")) return BLAST;
        if (burnt || s.isFireDamage()) return FIRE;
        if (s == DamageSource.drown || t.equals("drown")) return DROWN;
        if (fell || s == DamageSource.fall) return FALL;
        if (s.isProjectile() || t.contains("bullet") || t.equals("arrow")) return SHOT;
        if (s.isMagicDamage() || s == DamageSource.wither || s == DamageSource.starve || t.contains("poison"))
            return SICK;
        if (s.getEntity() != null) return MELEE;
        return GENERIC;
    }

    /** A look for a body in this pose dying of cause: variant, mirror, jitter. */
    public static int choose(Random r, int pose, int cause) {
        float[][] t = TABLE[clamp(pose)];
        int fit = 0, any = 0;
        for (float[] v : t) {
            if (cause != GENERIC && (int) v[0] == cause) fit++;
            else if ((int) v[0] == GENERIC) any++;
        }
        boolean fitting = fit > 0 && r.nextFloat() < FITTING;
        int n = r.nextInt(fitting ? fit : any), variant = 0;
        for (int i = 0; i < t.length; i++) {
            int c = (int) t[i][0];
            if (fitting ? c == cause : c == GENERIC) if (n-- == 0) {
                variant = i;
                break;
            }
        }
        return look(variant, r.nextBoolean(), r.nextInt(256), cause);
    }

    public static int look(int variant, boolean flip, int jitter, int cause) {
        return variant & 127 | (flip ? 128 : 0) | (jitter & 255) << 8 | (cause & 15) << 16;
    }

    public static int variant(int look) {
        return look & 127;
    }

    public static boolean flipped(int look) {
        return (look & 128) != 0;
    }

    public static int causeOf(int look) {
        return look >> 16 & 15;
    }

    /** A look saved before there were more than four variants: variant & 3, mirror in bit 4, no jitter. */
    public static int fromOld(int old) {
        return look(old & 3, (old & 4) != 0, 0, GENERIC) | 1 << 20; // bit 20: no jitter, as it was
    }

    /** Degrees the body rolls onto its side: positive puts the right side down. 0 for other poses. */
    public static float roll(int pose, int look) {
        if (pose != SIDE) return 0;
        float[] v = entry(pose, look);
        float r = v[1] + ((look & 1 << 20) != 0 ? 0 : (noise(look, 15) - 0.5F) * 14);
        return flipped(look) ? -r : r;
    }

    private static float[] entry(int pose, int look) {
        float[][] t = TABLE[clamp(pose)];
        return t[Math.min(variant(look), t.length - 1)];
    }

    /** As rest(), with the limbs in tuck (bit per Skeleton part) pulled in: they'd go into a wall otherwise. */
    public static void rest(int pose, int look, int situation, float bend, int tuck, float[] out) {
        rest(pose, look, situation, bend, out);
        if (tuck != 0) tuck(pose, tuck, out);
    }

    /**
     * Pulls in what would go into a block: an arm along the side, a leg straight, the head turned square, a sitting
     * body's legs drawn up (knees as high as a straight leg can show them).
     */
    static void tuck(int pose, int mask, float[] a) {
        if ((mask & 1) != 0) {
            a[1] = 0;
            a[2] = 0;
        }
        for (int arm = 0; arm < 2; arm++) if ((mask & 1 << (2 + arm)) != 0) {
            int i = 3 + arm * 3;
            float in = arm == 0 ? 1 : -1; // a hair out from the side, so it doesn't sink into the trunk
            a[i] = pose == PRONE ? 0.1F : pose == SIT ? -0.35F : pose == SIDE ? -0.9F : -0.05F;
            a[i + 1] = 0;
            a[i + 2] = pose == SIDE ? 0 : 0.04F * in;
        }
        for (int leg = 0; leg < 2; leg++) if ((mask & 1 << (4 + leg)) != 0) {
            int i = 9 + leg * 3;
            a[i] = pose == SIT ? -1.05F : pose == PRONE ? 0.08F : pose == SIDE ? -0.5F : -0.05F;
            a[i + 1] = 0;
            a[i + 2] = leg == 0 ? 0.02F : -0.02F;
        }
    }

    /** The resting angles of a body: its variant, mirrored when flipped, jittered, then bent by the ground. */
    public static void rest(int pose, int look, int situation, float bend, float[] out) {
        float[] v = entry(pose, look);
        if (!flipped(look)) System.arraycopy(v, 2, out, 0, 15);
        else for (int i = 0; i < 15; i += 3) {
            // a mirror image: the limbs trade sides and what turned out or sideways turns the other way
            int from = 2 + (i == 3 ? 6 : i == 6 ? 3 : i == 9 ? 12 : i == 12 ? 9 : i);
            out[i] = v[from];
            out[i + 1] = -v[from + 1];
            out[i + 2] = -v[from + 2];
        }
        if ((look & 1 << 20) == 0) for (int i = 0; i < 15; i++) out[i] += (noise(look, i) - 0.5F) * 2 * JITTER[i];
        Situation.apply(situation, bend, out);
    }

    /** 0..1, fixed per jitter seed and slot. */
    private static float noise(int look, int slot) {
        int h = (look >> 8 & 255) * 0x9E3779B1 + slot * 0x85EBCA77;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return (h & 0xFFFF) / 65535F;
    }
}
