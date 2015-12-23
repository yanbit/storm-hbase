package backtype.storm.contrib.hbase.examples;

import backtype.storm.Config;
import backtype.storm.StormSubmitter;
import backtype.storm.contrib.hbase.trident.HBaseAggregateState;
import backtype.storm.contrib.hbase.utils.TridentConfig;
import backtype.storm.generated.AlreadyAliveException;
import backtype.storm.generated.AuthorizationException;
import backtype.storm.generated.InvalidTopologyException;
import backtype.storm.spout.SchemeAsMultiScheme;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Values;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import storm.kafka.BrokerHosts;
import storm.kafka.StringScheme;
import storm.kafka.ZkHosts;
import storm.kafka.trident.TransactionalTridentKafkaSpout;
import storm.kafka.trident.TridentKafkaConfig;
import storm.trident.TridentTopology;
import storm.trident.operation.BaseFunction;
import storm.trident.operation.TridentCollector;
import storm.trident.operation.builtin.Count;
import storm.trident.state.StateFactory;
import storm.trident.tuple.TridentTuple;

import java.util.UUID;

/**
 * An example Storm Trident topology that uses {@link HBaseAggregateState} for
 * stateful stream processing.
 * <p>
 * This example persists idempotent counts in HBase for the number of times a
 * shortened URL has been seen in the stream for each day, week, and month.
 * <p>
 * Assumes the HBase table has been created.<br>
 * <tt>create 'shorturl', {NAME => 'data', VERSIONS => 3}, 
 * {NAME => 'daily', VERSION => 1, TTL => 604800}, 
 * {NAME => 'weekly', VERSION => 1, TTL => 2678400}, 
 * {NAME => 'monthly', VERSION => 1, TTL => 31536000}</tt>
 */
public class TestHBase {

  /**
   * Partitions a tuple into three to represent daily, weekly, and monthly stats
   * <p>
   * For example, when passed the following tuple:<br>
   * {shortid:http://bit.ly/ZK6t, date:20120816}
   * <p>
   * The function will output the following three tuples:<br>
   * {shortid:http://bit.ly/ZK6t, date:20120816, cf:daily, cq:20120816}<br>
   * {shortid:http://bit.ly/ZK6t, date:20120816, cf:weekly, cq:201233}<br>
   * {shortid:http://bit.ly/ZK6t, date:20120816, cf:monthly, cq:201208}
   */
  @SuppressWarnings("serial")
  static class DatePartitionFunction extends BaseFunction {
    final String cfStatsDaily = "daily";
    final String cfStatsWeekly = "weekly";
    final String cfStatsMonthly = "monthly";
    final static transient DateTimeFormatter dtf = DateTimeFormat
        .forPattern("YYYYMMdd");

    @Override
    public void execute(TridentTuple tuple, TridentCollector collector) {
      String monthly = tuple.getString(1).substring(0, 6);
      Integer week = dtf.parseDateTime(tuple.getString(1)).getWeekOfWeekyear();
      String weekly = tuple.getString(1).substring(0, 4)
          .concat(week.toString());

      collector.emit(new Values(cfStatsDaily, tuple.getString(1)));
      collector.emit(new Values(cfStatsMonthly, monthly));
      collector.emit(new Values(cfStatsWeekly, weekly));
    }
  }

  /**
   * @param args
   * @throws InterruptedException
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static void main(String[] args) throws InterruptedException, InvalidTopologyException, AuthorizationException, AlreadyAliveException {
    BrokerHosts hosts = new ZkHosts("datanode1:2181,datanode2:2181,datanode4:2181");
    TridentKafkaConfig tridentKafkaConfig =
            new TridentKafkaConfig(hosts, "test_request_count", UUID.randomUUID().toString());
    tridentKafkaConfig.ignoreZkOffsets=true;
    tridentKafkaConfig.scheme = new SchemeAsMultiScheme(new StringScheme());
    TransactionalTridentKafkaSpout tridentKafkaSpout = new TransactionalTridentKafkaSpout(tridentKafkaConfig);

    TridentConfig config = new TridentConfig("test", "key");
    config.setBatch(true);
    config.addColumn("f","count");
    StateFactory state = HBaseAggregateState.transactional(config);

    TridentTopology topology = new TridentTopology();
    topology
        .newStream("spout", tridentKafkaSpout)
        .each(new Fields("str"), new TestHBaseETL(), new Fields("key"))
        .groupBy(new Fields("key"))
        .persistentAggregate(state, new Count(), new Fields("count"));

    Config conf = new Config();
    conf.setNumWorkers(1);
    StormSubmitter.submitTopology("hbase-trident-aggregate", conf, topology.build());

  }
}
