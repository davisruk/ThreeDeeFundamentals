package online.davisfamily.warehouse.sim.dsp.io;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TwelveNDatasetLoader {

    public List<TwelveNMessageJson> load(List<Path> paths) {
        if (paths == null) {
            throw new IllegalArgumentException("paths must not be null");
        }
        List<TwelveNMessageJson> messages = new ArrayList<>();
        for (Path path : paths) {
            if (path == null) {
                throw new IllegalArgumentException("paths must not contain null");
            }
            messages.add(JsonLoaderSupport.read(path, TwelveNMessageJson.class));
        }
        return List.copyOf(messages);
    }

    public TwelveNMessageJson loadString(String json) {
        return JsonLoaderSupport.readString(json, TwelveNMessageJson.class);
    }

    public List<TwelveNMessageJson> loadStrings(List<String> jsonMessages) {
        if (jsonMessages == null) {
            throw new IllegalArgumentException("jsonMessages must not be null");
        }
        List<TwelveNMessageJson> messages = new ArrayList<>();
        for (String json : jsonMessages) {
            if (json == null) {
                throw new IllegalArgumentException("jsonMessages must not contain null");
            }
            messages.add(loadString(json));
        }
        return List.copyOf(messages);
    }
}
