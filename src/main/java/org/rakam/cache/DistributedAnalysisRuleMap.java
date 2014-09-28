package org.rakam.cache;

import org.rakam.analysis.AnalysisRuleParser;
import org.rakam.analysis.rule.aggregation.AnalysisRule;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by buremba on 22/12/13.
 */
public class DistributedAnalysisRuleMap implements Handler<Message<JsonObject>> {
    public static final String IDENTIFIER = "aggregationRuleReplication";
    public final static int ADD = 0;
    public final static int DELETE = 1;
    public final static int UPDATE_BATCH = 2;

    static Map<String, Set<AnalysisRule>> map = new HashMap();

    public static synchronized void merge(Map<String, Set<AnalysisRule>> rules) {
        rules.forEach((k, v) -> map.put(k, v));
    }

    public static Set<AnalysisRule> get(String project) {
        return map.get(project);
    }

    public static Set<Map.Entry<String, Set<AnalysisRule>>> entrySet() {
        return map.entrySet();
    }

    public static Set<String> keys() {
        return map.keySet();
    }

    public synchronized static void add(String project, AnalysisRule rule) {
        map.computeIfAbsent(project, x -> new HashSet()).add(rule);
    }

    public synchronized static void remove(String project, AnalysisRule rule) {
        map.computeIfPresent(project, (x, v) -> {
            v.remove(rule);
            return v;
        });
    }

    private synchronized void updateBatch(String project, AnalysisRule rule) {
        map.computeIfPresent(project, (x, v) -> {
            v.forEach(r -> {
                if(r.equals(r)) rule.batch_status = true;
            });
            return v;
        });
    }


    // check operation timestamp and compare the current version
    // because the request may be processed unordered and it may cause data loss.
    @Override
    public void handle(Message<JsonObject> message) {
        JsonObject json = message.body();
        String project = json.getString("_tracking");
        String id = json.getString("_id");
        if (id == null)
            throw new IllegalArgumentException("id for event must be provided");

        switch (json.getInteger("operation")) {
            case ADD:
                add(project, AnalysisRuleParser.parse(json.getObject("rule")));
                break;
            case DELETE:
                remove(project, AnalysisRuleParser.parse(json.getObject("rule")));
            case UPDATE_BATCH:
                updateBatch(project, AnalysisRuleParser.parse(json.getObject("rule")));
            default:
                throw new IllegalArgumentException("operation doesn't exist");
        }
    }



    public static void clear() {
        map.clear();
    }
}