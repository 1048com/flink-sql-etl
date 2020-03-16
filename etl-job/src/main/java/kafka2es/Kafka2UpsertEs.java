package kafka2es;

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.java.StreamTableEnvironment;
import org.apache.flink.table.functions.ScalarFunction;

import java.sql.Timestamp;

public class Kafka2UpsertEs {
    private static String csvSourceDDL = "create table csv(" +
            " pageId VARCHAR," +
            " eventId VARCHAR," +
            " recvTime VARCHAR" +
            ") with (" +
            " 'connector.type' = 'filesystem',\n" +
            " 'connector.path' = '/Users/bang/sourcecode/project/flink-sql-etl/data-generator/src/main/resources/user3.csv',\n" +
            " 'format.type' = 'csv',\n" +
            " 'format.fields.0.name' = 'pageId',\n" +
            " 'format.fields.0.data-type' = 'STRING',\n" +
            " 'format.fields.1.name' = 'eventId',\n" +
            " 'format.fields.1.data-type' = 'STRING',\n" +
            " 'format.fields.2.name' = 'recvTime',\n" +
            " 'format.fields.2.data-type' = 'STRING')";
    private static String sinkDDL = "CREATE TABLE ES6_ZHANGLE_OUTPUT (\n" +
            "  aggId varchar ,\n" +
            "  pageId varchar ,\n" +
            "  ts varchar ,\n" +
            "  expoCnt bigint ,\n" +
            "  clkCnt bigint\n" +
            ") WITH (\n" +
            "'connector.type' = 'elasticsearch',\n" +
            "'connector.version' = '6',\n" +
            "'connector.hosts' = 'http://localhost:9200',\n" +
            "'connector.index' = 'flink_zhangle_pageview',\n" +
            "'connector.document-type' = '_doc',\n" +
            "'update-mode' = 'upsert',\n" +
            "'connector.key-delimiter' = '$',\n" +
            "'connector.key-null-literal' = 'n/a',\n" +
            "'connector.bulk-flush.interval' = '1000',\n" +
            "'format.type' = 'json'\n" +
            ")\n";
    private static String query = "INSERT INTO test_upsert\n" +
            "  SELECT aggId, pageId, ts,\n" +
            "  count(case when eventId = 'exposure' then 1 else null end) as expoCnt,\n" +
            "  count(case when eventId = 'click' then 1 else null end) as clkCnt\n" +
            "  FROM\n" +
            "  (\n" +
            "    SELECT\n" +
            "        'ZL_001' as aggId,\n" +
            "        pageId,\n" +
            "        eventId,\n" +
            "        recvTime,\n" +
            "        ts2Date(recvTime) as ts\n" +
            "    from csv\n" +
            "    where eventId in ('exposure', 'click')\n" +
            "  ) as t1\n" +
            "  group by aggId, pageId, ts";

    private static String sinkBlinkDDL = "CREATE TABLE ES6_ZHANGLE_OUTPUT (\n" +
            "  aggId varchar ,\n" +
            "  pageId varchar ,\n" +
            "  ts varchar ,\n" +
            "  expoCnt bigint ,\n" +
            "  clkCnt bigint\n" +
            ") WITH (\n" +
            "'connector.type' = 'elasticsearch',\n" +
            "'connector.version' = '6',\n" +
            "'connector.hosts' = 'http://localhost:9200',\n" +
            "'connector.index' = 'blink_pageview',\n" +
            "'connector.document-type' = '_doc',\n" +
            "'update-mode' = 'upsert',\n" +
            "'connector.key-delimiter' = '$',\n" +
            "'connector.key-null-literal' = 'n/a',\n" +
            "'connector.bulk-flush.interval' = '1000',\n" +
            "'format.type' = 'json'\n" +
            ")\n";
    private static String sinkMysqlDDL =  "CREATE TABLE test_upsert (\n" +
            "  aggId STRING ,\n" +
            "  pageId STRING ,\n" +
            "  ts STRING ,\n" +
            "  expoCnt BIGINT ,\n" +
            "  clkCnt BIGINT\n" +
            ") WITH (\n" +
            "   'connector.type' = 'jdbc',\n" +
            "   'connector.url' = 'jdbc:mysql://localhost:3306/test',\n" +
            "   'connector.username' = 'root'," +
            "   'connector.table' = 'test_upsert',\n" +
            "   'connector.driver' = 'com.mysql.jdbc.Driver',\n" +
            "   'connector.write.flush.max-rows' = '5000', \n" +
            "   'connector.write.flush.interval' = '2s', \n" +
            "   'connector.write.max-retries' = '3'" +
            ")";

    public static void main(String[] args) throws Exception {
        // legacy planner test passed
//         testLegacyPlanner();

        // blink planner test passed
        testBlinkPlanner();
    }

    public static void testLegacyPlanner() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        EnvironmentSettings envSettings = EnvironmentSettings.newInstance()
                .useOldPlanner()
                .inStreamingMode()
                .build();
        StreamTableEnvironment tableEnvironment = StreamTableEnvironment.create(env, envSettings);
        tableEnvironment.registerFunction("ts2Date", new ts2Date());

        tableEnvironment.sqlUpdate(csvSourceDDL);
        tableEnvironment.sqlUpdate(sinkDDL);
        tableEnvironment.sqlUpdate(query);

        tableEnvironment.execute("Kafka2Es");
    }

    public static void testBlinkPlanner() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        EnvironmentSettings envSettings = EnvironmentSettings.newInstance()
                .useBlinkPlanner()
                .inStreamingMode()
                .build();
        StreamTableEnvironment tableEnvironment = StreamTableEnvironment.create(env, envSettings);
        tableEnvironment.registerFunction("ts2Date", new ts2Date());
        tableEnvironment.sqlUpdate(csvSourceDDL);
        tableEnvironment.sqlUpdate(sinkMysqlDDL);

        String queryTest =  "  SELECT aggId, pageId, ts,\n" +
                "  count(case when eventId = 'exposure' then 1 else null end) as expoCnt,\n" +
                "  count(case when eventId = 'click' then 1 else null end) as clkCnt\n" +
                "  FROM\n" +
                "  (\n" +
                "    SELECT\n" +
                "        'ZL_001' as aggId,\n" +
                "        pageId,\n" +
                "        eventId,\n" +
                "        recvTime,\n" +
                "        ts2Date(recvTime) as ts\n" +
                "    from csv\n" +
                "    where eventId in ('exposure', 'click')\n" +
                "  ) as t1\n" +
                "  group by aggId, pageId, ts";;
         System.out.println(tableEnvironment.explain(tableEnvironment.sqlQuery(queryTest)));

        tableEnvironment.sqlUpdate(query);

        tableEnvironment.execute("Kafka2Es");
    }

    public static class ts2Date extends ScalarFunction {
        public String eval(String timeStr) {
            Timestamp t = Timestamp.valueOf(timeStr);
            return t.getDate() + " " + t.getHours() + "：" + t.getMinutes();
        }

        public TypeInformation<?> getResultType(Class<?>[] signature) {
            return Types.STRING;
        }
    }
}
