package backtype.storm.contrib.hbase.examples;

import backtype.storm.tuple.Values;
import storm.trident.operation.BaseFunction;
import storm.trident.operation.TridentCollector;
import storm.trident.tuple.TridentTuple;

/**
 * User: yanbit
 * Date: 2015/12/23
 * Time: 10:30
 */
public class TestHBaseETL extends BaseFunction {
    @Override
    public void execute(TridentTuple tuple, TridentCollector collector) {
        // TODO need etl ?
        collector.emit(new Values(tuple.getString(0)));
    }
}
