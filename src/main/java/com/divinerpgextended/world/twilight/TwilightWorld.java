package com.divinerpgextended.world.twilight;

import net.divinerpg.utils.blocks.TwilightBlocks;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.divinerpg.utils.items.TwilightItemsOther;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLiving;
import net.minecraft.item.Item;

/** Сумеречные миры для построек аддона: блоки мира, его души и камни, мобы для стражи и спавнеров. */
public enum TwilightWorld {

    EDEN, WILDWOOD, APALACHIA, SKYTHERN, MORTUM;

    public int dimension() {
        switch (this) {
            case EDEN: return ConfigurationHelper.eden;
            case WILDWOOD: return ConfigurationHelper.wildwood;
            case APALACHIA: return ConfigurationHelper.apalachia;
            case SKYTHERN: return ConfigurationHelper.skythern;
            default: return ConfigurationHelper.mortum;
        }
    }

    public static TwilightWorld of(int dim) {
        for (TwilightWorld w : values()) if (w.dimension() == dim) return w;
        return null;
    }

    public String key() {
        return name().toLowerCase();
    }

    public Block grass() {
        switch (this) {
            case EDEN: return TwilightBlocks.edenGrass;
            case WILDWOOD: return TwilightBlocks.wildwoodGrass;
            case APALACHIA: return TwilightBlocks.apalachiaGrass;
            case SKYTHERN: return TwilightBlocks.skythernGrass;
            default: return TwilightBlocks.mortumGrass;
        }
    }

    public Block dirt() {
        switch (this) {
            case EDEN: return TwilightBlocks.edenDirt;
            case WILDWOOD: return TwilightBlocks.wildwoodDirt;
            case APALACHIA: return TwilightBlocks.apalachiaDirt;
            case SKYTHERN: return TwilightBlocks.skythernDirt;
            default: return TwilightBlocks.mortumDirt;
        }
    }

    public Block log() {
        switch (this) {
            case EDEN: return TwilightBlocks.edenLogs;
            case WILDWOOD: return TwilightBlocks.wildwoodLogs;
            case APALACHIA: return TwilightBlocks.apalachiaLogs;
            case SKYTHERN: return TwilightBlocks.skythernLogs;
            default: return TwilightBlocks.mortumLogs;
        }
    }

    public Block leaves() {
        switch (this) {
            case EDEN: return TwilightBlocks.edenLeaves;
            case WILDWOOD: return TwilightBlocks.wildwoodLeaves;
            case APALACHIA: return TwilightBlocks.apalachiaLeaves;
            case SKYTHERN: return TwilightBlocks.skythernLeaves;
            default: return TwilightBlocks.mortumLeaves;
        }
    }

    public Block planks() {
        switch (this) {
            case EDEN: return TwilightBlocks.edenPlanks;
            case WILDWOOD: return TwilightBlocks.wildwoodPlanks;
            case APALACHIA: return TwilightBlocks.apalachiaPlanks;
            case SKYTHERN: return TwilightBlocks.skythernPlanks;
            default: return TwilightBlocks.mortumPlanks;
        }
    }

    public Block slab() {
        switch (this) {
            case EDEN: return TwilightBlocks.edenSlab;
            case WILDWOOD: return TwilightBlocks.wildwoodSlab;
            case APALACHIA: return TwilightBlocks.apalachiaSlab;
            case SKYTHERN: return TwilightBlocks.skythernSlab;
            default: return TwilightBlocks.mortumSlab;
        }
    }

    public Block ore() {
        switch (this) {
            case EDEN: return TwilightBlocks.edenOre;
            case WILDWOOD: return TwilightBlocks.wildwoodOre;
            case APALACHIA: return TwilightBlocks.apalachiaOre;
            case SKYTHERN: return TwilightBlocks.skythernOre;
            default: return TwilightBlocks.mortumOre;
        }
    }

    /** Блок из камней мира (edenBlock и т.п.). */
    public Block gemBlock() {
        switch (this) {
            case EDEN: return TwilightBlocks.edenBlock;
            case WILDWOOD: return TwilightBlocks.wildwoodBlock;
            case APALACHIA: return TwilightBlocks.apalachiaBlock;
            case SKYTHERN: return TwilightBlocks.skythernBlock;
            default: return TwilightBlocks.mortumBlock;
        }
    }

    public Item soul() {
        switch (this) {
            case EDEN: return TwilightItemsOther.edenSoul;
            case WILDWOOD: return TwilightItemsOther.wildwoodSoul;
            case APALACHIA: return TwilightItemsOther.apalachiaSoul;
            case SKYTHERN: return TwilightItemsOther.skythernSoul;
            default: return TwilightItemsOther.mortumSoul;
        }
    }

    public Item gem() {
        switch (this) {
            case EDEN: return TwilightItemsOther.edenGem;
            case WILDWOOD: return TwilightItemsOther.wildwoodGem;
            case APALACHIA: return TwilightItemsOther.apalachiaGem;
            case SKYTHERN: return TwilightItemsOther.skythernGem;
            default: return TwilightItemsOther.mortumGem;
        }
    }

    public Item fragments() {
        switch (this) {
            case EDEN: return TwilightItemsOther.edenFragments;
            case WILDWOOD: return TwilightItemsOther.wildwoodFragments;
            case APALACHIA: return TwilightItemsOther.apalachiaFragments;
            case SKYTHERN: return TwilightItemsOther.skythernFragments;
            default: return TwilightItemsOther.mortumFragments;
        }
    }

    /** Мобы мира для стражи руин и спавнеров подземелий. */
    @SuppressWarnings("unchecked")
    public Class<? extends EntityLiving>[] mobs() {
        switch (this) {
            case EDEN: return new Class[] { net.divinerpg.entities.twilight.EntityEdenCori.class,
                net.divinerpg.entities.twilight.EntityEdenCadillion.class, net.divinerpg.entities.twilight.EntityGreenfeet.class };
            case WILDWOOD: return new Class[] { net.divinerpg.entities.twilight.EntityWildwoodGolem.class,
                net.divinerpg.entities.twilight.EntityWildwoodCadillion.class, net.divinerpg.entities.twilight.EntityVerek.class };
            case APALACHIA: return new Class[] { net.divinerpg.entities.twilight.EntityApalachiaGolem.class,
                net.divinerpg.entities.twilight.EntityApalachiaCadillion.class, net.divinerpg.entities.twilight.EntityApalachiaWarrior.class };
            case SKYTHERN: return new Class[] { net.divinerpg.entities.twilight.EntitySkythernFiend.class,
                net.divinerpg.entities.twilight.EntitySkythernArcher.class, net.divinerpg.entities.twilight.EntitySkythernGolem.class };
            default: return new Class[] { net.divinerpg.entities.twilight.EntityMortumCadillion.class,
                net.divinerpg.entities.twilight.EntityMortumDemon.class, net.divinerpg.entities.twilight.EntitySoulSpider.class };
        }
    }
}
