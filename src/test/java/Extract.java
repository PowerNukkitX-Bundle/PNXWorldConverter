import cn.coolloong.PNXWorldConverter;
import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

//DataSource
//biomes.json blocks.json items.json https://github.com/GeyserMC/mappings
//jeEnchantmentsMapping.json Manual make
//legacy_blocks.json https://github.com/matcool/anvil-parser/blob/master/anvil/legacy_blocks.json modify
//je1192DefaultBlockState.json https://github.com/PrismarineJS/minecraft-data/blob/master/data/pc/1.19/blocks.json modify
public class Extract {
    public static void main(String[] args) throws IOException, URISyntaxException {
        var gson = new Gson();
        var json = new File(PNXWorldConverter.class.getClassLoader().getResource("je112EnchantmentsId2Str.json").toURI());
        var json2 = new File(PNXWorldConverter.class.getClassLoader().getResource("jeEnchantmentsMapping.json").toURI());
        var map = (Map<String, String>) gson.fromJson(Files.readString(json.toPath()), Map.class);
        var map2 = (Map<String, Object>) gson.fromJson(Files.readString(json2.toPath()), Map.class);
        var result = new LinkedHashMap<>();
        for (var x : map.entrySet()) {
            System.out.println(x.getValue());
            result.put(x.getKey(), ((Number) map2.get(x.getValue())).intValue());
        }
        Files.writeString(Path.of("target/je112EnchId2PNXId.json"), gson.toJson(result), StandardCharsets.UTF_8);
    }
}
