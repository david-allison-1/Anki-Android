package com.ichi2.libanki.decks;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

public class ReviewingConf extends ReadOnlyJSONObject {
    public ReviewingConf() {
        super();
    }

    public ReviewingConf(ReviewingConf ReviewingConf) {
        super(ReviewingConf.getJSON());
    }

    public ReviewingConf(JSONObject json) {
        super(json);
    }

    public ReviewingConf(String json) {
        super(json);
    }

    public JSONArray getDelays(){
        return getJSON().getJSONArray("delays");
    }
}
