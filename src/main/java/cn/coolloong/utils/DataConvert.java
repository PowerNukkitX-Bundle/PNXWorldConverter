package cn.coolloong.utils;

import cn.coolloong.PNXWorldConverter;
import cn.coolloong.SupportVersion;
import cn.coolloong.proxy.ProxyChunk;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockstate.BlockState;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.utils.Config;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.jglrxavpok.hephaistos.nbt.*;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class DataConvert {
    private static final Map<String, Map<String, Object>> DEFAULT_BLOCKS_MAP = new HashMap<>();
    private static final Map<String, Integer> BIOMES_MAP = new HashMap<>();
    private static final Map<String, Map<String, Object>> ITEM_MAP = new HashMap<>();
    private static final Map<String, Short> ENCHANTMENT_MAP = new HashMap<>();
    private static final Map<String, List<?>> LEGACY_ID_2_BLOCKS = new HashMap<>();
    private static final Map<org.jglrxavpok.hephaistos.mca.BlockState, Map<String, Object>> JE_BLOCKS_MAPPING = new HashMap<>();
    public static final Map<Integer, Integer> JE112_ENCHID_2_PNXID = new HashMap<>();
    private static final Gson GSON = new Gson();

    private static final Cache<org.jglrxavpok.hephaistos.mca.BlockState, BlockState> BLOCK_STATE_CACHE = Caffeine.newBuilder().expireAfterWrite(Long.MAX_VALUE, TimeUnit.MINUTES).build();

    static {
        final var config = new Config(Config.JSON);
        try {
            config.load(PNXWorldConverter.class.getModule().getResourceAsStream("biomes.json"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        config.getAll().forEach((k, v) -> BIOMES_MAP.put(k, ((Number) ((Map<String, ?>) v).get("bedrock_id")).intValue()));

        try {
            config.load(PNXWorldConverter.class.getModule().getResourceAsStream("items.json"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //noinspection unchecked
        config.getAll().forEach((k, v) -> ITEM_MAP.put(k, (Map<String, Object>) v));

        try {
            config.load(PNXWorldConverter.class.getModule().getResourceAsStream("jeEnchantmentsMapping.json"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        config.getAll().forEach((k, v) -> ENCHANTMENT_MAP.put(k, ((Number) v).shortValue()));

        try {
            config.load(PNXWorldConverter.class.getModule().getResourceAsStream("je1192DefaultBlockState.json"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //noinspection unchecked
        config.getAll().forEach((k, v) -> DEFAULT_BLOCKS_MAP.put(k, (Map<String, Object>) v));

        try {
            config.load(PNXWorldConverter.class.getModule().getResourceAsStream("legacy_blocks.json"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        config.getAll().forEach((k, v) -> LEGACY_ID_2_BLOCKS.put(k, (List<?>) v));

        try {
            config.load(PNXWorldConverter.class.getModule().getResourceAsStream("je112EnchId2PNXId.json"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        config.getAll().forEach((k, v) -> JE112_ENCHID_2_PNXID.put(Math.round(Float.parseFloat(k)), ((Number) v).intValue()));

        try {
            config.load(PNXWorldConverter.class.getModule().getResourceAsStream("blocks.json"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        config.getAll().forEach((k, v) -> {
            var strings = k.replace("[", ",").replace("]", ",").replace(" ", "").split(",");
            var map = new HashMap<String, String>();
            if (strings.length > 0) {
                for (int i = 1; i < strings.length; i++) {
                    final var tmp = strings[i];
                    final var index = tmp.indexOf("=");
                    map.put(tmp.substring(0, index), tmp.substring(index + 1));
                }
            }
            var state = new org.jglrxavpok.hephaistos.mca.BlockState(strings[0], map);
            //noinspection unchecked
            JE_BLOCKS_MAPPING.put(state, (Map<String, Object>) v);
        });
    }

    public static org.jglrxavpok.hephaistos.mca.BlockState convertLegacyId(String LegacyId) {
        var list = LEGACY_ID_2_BLOCKS.get(LegacyId);
        if (list == null) {
//            try {
//                Files.write(Path.of("target/error.txt"), Collections.singleton(LegacyId), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
            Logger.warn("cant find legacyID:" + LegacyId);
            return new org.jglrxavpok.hephaistos.mca.BlockState("minecraft:air", Map.of());
        }
        var name = list.get(0).toString();
        //noinspection unchecked
        var map = (Map<String, Object>) list.get(1);
        if (map == null) return new org.jglrxavpok.hephaistos.mca.BlockState("minecraft:" + name, Map.of());
        var value = new LinkedHashMap<String, String>();
        for (var entry : map.entrySet()) {
            if (entry.getValue() instanceof Number number) {
                value.put(entry.getKey(), String.valueOf(number.intValue()));
            } else value.put(entry.getKey(), entry.getValue().toString());
        }
        return new org.jglrxavpok.hephaistos.mca.BlockState("minecraft:" + name, value);
    }

    public static BlockState convertBlockState(org.jglrxavpok.hephaistos.mca.BlockState sourceType) {
        //添加缓存
        var cache = BLOCK_STATE_CACHE.getIfPresent(sourceType);
        if (cache != null) return cache;
        var beState = JE_BLOCKS_MAPPING.get(sourceType);
        String beBlockName = null;
        Map<String, Object> beBlockState = null;

        if (beState != null) {
            beBlockName = beState.get("bedrock_identifier").toString();
            //noinspection unchecked
            beBlockState = (Map<String, Object>) beState.get("bedrock_states");
        } else {//基准版本1.19 由于不同版本数据值可能存在一定区别，当前映射文件是最新的，所以找不到就模糊匹配
            var defaultState = DEFAULT_BLOCKS_MAP.get(sourceType.getName());
            if (defaultState != null) {
                var map = new LinkedHashMap<>(sourceType.getProperties());
                for (var s1 : defaultState.keySet()) {
                    if (!sourceType.getProperties().containsKey(s1)) {
                        map.put(s1, defaultState.get(s1).toString());
                    }
                }
                var fixBeState = JE_BLOCKS_MAPPING.get(new org.jglrxavpok.hephaistos.mca.BlockState(sourceType.getName(), map));
                if (fixBeState == null) {
                    beBlockName = "minecraft:air";
                } else {
                    beBlockName = fixBeState.get("bedrock_identifier").toString();
                    //noinspection unchecked
                    beBlockState = (Map<String, Object>) fixBeState.get("bedrock_states");
                }
            } else {
                Logger.warn("cant find the default BlockState in " + sourceType.getName() + " block");
            }
        }

        if (beBlockState != null) {
            var nkState = new StringBuilder();
            beBlockState.forEach((k, v) -> {
                if (v.toString().equals("true") || v.toString().equals("false")) {
                    nkState.append(";").append(k).append("=").append(v.toString().equals("true") ? 1 : 0);
                } else if (v instanceof Number number) {
                    nkState.append(";").append(k).append("=").append(number.intValue());
                } else nkState.append(";").append(k).append("=").append(v);
            });
            var result = BlockState.of(beBlockName + nkState);
            BLOCK_STATE_CACHE.put(sourceType, result);
            return result;
        } else {
            try {
                var result = BlockState.of(beBlockName + "");
                BLOCK_STATE_CACHE.put(sourceType, result);
                return result;
            } catch (NoSuchElementException e) {
                Logger.warn("cant find: " + beBlockName);
                BLOCK_STATE_CACHE.put(sourceType, BlockState.AIR);
                return BlockState.AIR;
            }
        }
    }

    public static int convertBiomes(String sourceType) {
        try {
            return BIOMES_MAP.get(sourceType);
        } catch (Exception e) {
            Logger.warn(sourceType);
            return 0;
        }
    }

    @SuppressWarnings("RedundantLabeledSwitchRuleCodeBlock")
    public static cn.nukkit.nbt.tag.Tag convertNBT(org.jglrxavpok.hephaistos.nbt.NBT sourceType) {
        switch (sourceType.getID().getOrdinal()) {
            case 0 -> {
                return new EndTag();
            }
            case 1 -> {
                return new ByteTag("", ((NBTByte) sourceType).getValue());
            }
            case 2 -> {
                return new ShortTag("", ((NBTShort) sourceType).getValue());
            }
            case 3 -> {
                return new IntTag("", ((NBTInt) sourceType).getValue());
            }
            case 4 -> {
                return new LongTag("", ((NBTLong) sourceType).getValue());
            }
            case 5 -> {
                return new FloatTag("", ((NBTFloat) sourceType).getValue());
            }
            case 6 -> {
                return new DoubleTag("", ((NBTDouble) sourceType).getValue());
            }
            case 7 -> {
                return new ByteArrayTag("", ((NBTByteArray) sourceType).getValue().copyArray());
            }
            case 8 -> {
                return new StringTag("", ((NBTString) sourceType).getValue());
            }
            case 9 -> {
                var list = ((NBTList<?>) sourceType);
                var tags = new ArrayList<Tag>();
                switch (list.getSubtagType().getOrdinal()) {
                    case 0 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(new EndTag()));
                    }
                    case 1 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(new ByteTag("", ((NBTByte) k).getValue())));
                    }
                    case 2 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(new ShortTag("", ((NBTShort) k).getValue())));
                    }
                    case 3 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(new IntTag("", ((NBTInt) k).getValue())));
                    }
                    case 4 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(new LongTag("", ((NBTLong) k).getValue())));
                    }
                    case 5 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(new FloatTag("", ((NBTFloat) k).getValue())));
                    }
                    case 6 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(new DoubleTag("", ((NBTDouble) k).getValue())));
                    }
                    case 7 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(new ByteArrayTag("", ((NBTByteArray) k).getValue().copyArray())));
                    }
                    case 8 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(new StringTag("", ((NBTString) k).getValue())));
                    }
                    case 9, 10 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(convertNBT(k)));
                    }
                    case 11 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(new IntArrayTag("", ((NBTIntArray) k).getValue().copyArray())));
                    }
                    case 12 -> {
                        list.forEach((Consumer<NBT>) k -> {
                            var result = new ListTag<LongTag>("");
                            for (long i : ((NBTLongArray) k).getValue().copyArray()) {
                                result.add(new LongTag("", i));
                            }
                            tags.add(result);
                        });
                    }
                }
                var result = new ListTag<>("");
                for (var value : tags) {
                    result.add(value);
                }
                return result;
            }
            case 10 -> {
                var list = ((NBTCompound) sourceType);
                var cmp = new CompoundTag("");
                for (var entry : list.getEntries()) {
                    cmp.put(entry.getKey(), convertNBT(entry.getValue()));
                }
                return cmp;
            }
            case 11 -> {
                return new IntArrayTag("", ((NBTIntArray) sourceType).getValue().copyArray());
            }
            case 12 -> {
                var result = new ListTag<LongTag>("");
                for (long i : ((NBTLongArray) sourceType).getValue().copyArray()) {
                    result.add(new LongTag("", i));
                }
                return result;
            }
        }
        return new EndTag();
    }

    public static void convertItem(String sourceType, CompoundTag compoundTag, SupportVersion version) {
        String bedrock_identifier = sourceType;

        if (version != SupportVersion.MC_OLD) {
            var map = ITEM_MAP.get(sourceType);
            short bedrock_data;
            if (map != null) {
                bedrock_identifier = (String) map.get("bedrock_identifier");
                bedrock_data = ((Number) ITEM_MAP.get(sourceType).get("bedrock_data")).shortValue();
            } else {
                bedrock_data = 0;
            }
            compoundTag.putShort("Damage", bedrock_data);
        }

        compoundTag.putString("Name", bedrock_identifier);
        compoundTag.remove("id");
    }

    @Nullable
    public static Short convertEnchantment(String sourceType) {
        return ENCHANTMENT_MAP.get(sourceType);
    }

    //末影箱物品 漏斗物品
    public static void convertTileEntities(ProxyChunk chunk, BlockStateSupplier blockStateSupplier, org.jglrxavpok.hephaistos.nbt.NBTList<NBTCompound> tileEntities, SupportVersion version) {
        var chestList = new LinkedList<CompoundTag>();
        var trappedChestList = new LinkedList<CompoundTag>();
        tileEntities.asListView().forEach(nbt -> {
            var cmp = (CompoundTag) convertNBT(nbt);
            //remove unknown
            if (version == SupportVersion.MC_OLD) {
                cmp.remove("Lock");
            } else if (version == SupportVersion.MC_NEW) {
                cmp.remove("keepPacked");
            }
            //handle item in block entity
            if (cmp.contains("Items")) {
                for (var i : cmp.getList("Items", CompoundTag.class).getAll()) {
                    if (i.containsCompound("tag")) {
                        var tag = i.getCompound("tag");
                        if (tag.contains("Damage")) tag.remove("Damage");//不知道干啥的nbt,pnx没有所以移除
                        if (tag.contains("display")) {
                            String text;
                            try {
                                var jsonName = GSON.fromJson(tag.getCompound("display").getString("Name"), Map.class);
                                if (jsonName == null) text = tag.getCompound("display").getString("Name");
                                else text = jsonName.get("text").toString();
                                //jsonName.get("text")可空
                            } catch (JsonSyntaxException | NullPointerException e) {
                                text = tag.getCompound("display").getString("Name");
                            }
                            tag.getCompound("display").putString("Name", text);
                        }
                        if (version == SupportVersion.MC_OLD) {
                            if (tag.containsList("ench")) {
                                var list = tag.getList("ench", CompoundTag.class);
                                list.getAll().forEach(ench -> ench.putShort("id", JE112_ENCHID_2_PNXID.get(ench.getShort("id"))));
                                tag.put("ench", list);
                            }
                        } else if (version == SupportVersion.MC_NEW) {
                            if (tag.containsList("Enchantments")) {
                                var list = tag.getList("Enchantments", CompoundTag.class);
                                list.getAll().forEach(ench -> {
                                    var id = convertEnchantment(ench.getString("id"));
                                    if (id != null)
                                        ench.putShort("id", id);
                                });
                                tag.put("ench", list);
                                tag.remove("Enchantments");
                            }
                        }
                    }
                    convertItem(i.getString("id"), i, version);
                }
            }
            //handle entity state
            //noinspection ConstantConditions
            switch (nbt.get("id").getValue().toString()) {
                case "minecraft:chest" -> {
                    //noinspection ConstantConditions
                    var block = blockStateSupplier.get(((int) nbt.get("x").getValue()) & 15, (int) nbt.get("y").getValue(), ((int) nbt.get("z").getValue()) & 15);
                    if (version == SupportVersion.MC_OLD) {
                        cmp.putString("id", BlockEntity.CHEST);
                        chestList.add(cmp);
                    } else if (version == SupportVersion.MC_NEW) {
                        if (block.getProperties().get("type").equals("single")) {
                            cmp.putString("id", BlockEntity.CHEST);
                            chunk.tiles.add(cmp);
                        } else {
                            cmp.putString("id", BlockEntity.CHEST);
                            chestList.add(cmp);
                        }
                    }
                }
                case "minecraft:trapped_chest" -> {
                    //noinspection ConstantConditions
                    var block = blockStateSupplier.get(((int) nbt.get("x").getValue()) & 15, (int) nbt.get("y").getValue(), ((int) nbt.get("z").getValue()) & 15);
                    if (version == SupportVersion.MC_OLD) {
                        cmp.putString("id", BlockEntity.CHEST);
                        trappedChestList.add(cmp);
                    } else if (version == SupportVersion.MC_NEW) {
                        if (block.getProperties().get("type").equals("single")) {
                            cmp.putString("id", BlockEntity.CHEST);
                            chunk.tiles.add(cmp);
                        } else {
                            cmp.putString("id", BlockEntity.CHEST);
                            trappedChestList.add(cmp);
                        }
                    }
                }
                case "minecraft:furnace" -> {
                    cmp.putString("id", BlockEntity.FURNACE);
                    chunk.tiles.add(cmp);
                }
                case "minecraft:barrel" -> {
                    cmp.putString("id", BlockEntity.BARREL);
                    chunk.tiles.add(cmp);
                }
                case "minecraft:dispenser" -> {
                    cmp.putString("id", BlockEntity.DISPENSER);
                    chunk.tiles.add(cmp);
                }
                case "minecraft:dropper" -> {
                    cmp.putString("id", BlockEntity.DROPPER);
                    chunk.tiles.add(cmp);
                }
                case "minecraft:hopper" -> {
                    cmp.putString("id", BlockEntity.HOPPER);
                    chunk.tiles.add(cmp);
                }
                case "minecraft:shulker_box" -> {
                    //noinspection ConstantConditions
                    var block = blockStateSupplier.get(((int) nbt.get("x").getValue()) & 15, (int) nbt.get("y").getValue(), ((int) nbt.get("z").getValue()) & 15);
                    byte facing = switch (block.getProperties().get("facing")) {
                        case "up" -> 1;
                        case "north" -> 2;
                        case "south" -> 3;
                        case "west" -> 4;
                        case "east" -> 5;
                        default -> 0;
                    };
                    cmp.putString("id", BlockEntity.SHULKER_BOX);
                    cmp.putByte("facing", facing);
                    chunk.tiles.add(cmp);
                }
                case "minecraft:beacon" -> {
                    cmp.putString("id", BlockEntity.BEACON);
                    chunk.tiles.add(cmp);
                }
                case "minecraft:beehive" -> {
                    cmp.putString("id", BlockEntity.BEEHIVE);
                    chunk.tiles.add(cmp);
                }
            }
        });
        //handle double chest
        convertDoubleChestEntity(chunk, chestList);
        convertDoubleChestEntity(chunk, trappedChestList);
    }

    private static void convertDoubleChestEntity(ProxyChunk chunk, LinkedList<CompoundTag> list) {
        for (int i = 0; i < list.size(); i++) {
            var pair1 = list.get(i);
            for (int j = i + 1; j < list.size(); j++) {
                var pair2 = list.get(j);
                int pair1y = pair1.getInt("y");
                int pair2y = pair2.getInt("y");
                if (pair1y == pair2y) {
                    int pair1x = pair1.getInt("x");
                    int pair2x = pair2.getInt("x");
                    int pair1z = pair1.getInt("z");
                    int pair2z = pair2.getInt("z");
                    if ((pair1z == pair2z && Math.abs(pair1x - pair2x) <= 1) || (pair1x == pair2x && Math.abs(pair1z - pair2z) <= 1)) {
                        pair1.putInt("pairx", pair2x);
                        pair1.putInt("pairz", pair2z);
                        pair2.putInt("pairx", pair1x);
                        pair2.putInt("pairz", pair1z);
                        chunk.tiles.add(pair1);
                        chunk.tiles.add(pair2);
                        list.remove(pair1);
                        list.remove(pair2);
                    }
                }
            }
        }
    }

}
