import cn.coolloong.PNXWorldConverter;
import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Extract {
    public static void main(String[] args) throws IOException, URISyntaxException {
        var gson = new Gson();
        var json = new File(PNXWorldConverter.class.getClassLoader().getResource("je1192DefaultBlockState.json").toURI());
        var list = gson.fromJson(Files.readString(json.toPath()), List.class);
        var result = new LinkedHashMap<>();
        for (var x : list) {
            var cmp = (Map<String, ?>) x;
            var states = (List<Map<String, ?>>) cmp.get("states");
            var result1 = new LinkedHashMap<>();
            for (var state : states) {
                var v = switch (state.get("type").toString()) {
                    case "int", "enum" -> ((List) state.get("values")).get(0).toString();
                    case "bool" -> "false";
                    default -> "";
                };
                result1.put(state.get("name"), v);
            }
            result.put("minecraft:" + cmp.get("name"), result1);
        }
        Files.writeString(Path.of("target/je1192DefaultBlockState.json"), gson.toJson(result), StandardCharsets.UTF_8);
    }
}
