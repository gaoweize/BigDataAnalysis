package Test.GMM.mapreduce;

import java.io.IOException;

import Test.GMM.utils.GaussianParams;
import Test.GMM.utils.PosteriorProbability;
import Test.GMM.utils.Stats;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;


public class GmmMapper extends Mapper<Object, Text, IntWritable, Stats> {

	@Override
	protected void map(Object key, Text value, Mapper<Object, Text, IntWritable, Stats>.Context context) throws IOException, InterruptedException {

		Configuration conf = context.getConfiguration();

		int k = conf.getInt("k", -1);
		if (k <= 0) {
			throw new RuntimeException("Cannot run GMM for zero Gaussians!");
		}

		// Parse input vector 
//		Log.info("input vector: " + value.toString());
		String[] split = value.toString().split("\\s+");
		int d = split.length;
		double[] x = new double[d];
		for (int dim = 0; dim < d; dim++) {
			x[dim] = Double.parseDouble(split[dim]);
		}
		// Load params from hdfs
		String paramsFilename = conf.getStrings("initParams")[0];
		GaussianParams[] params = GaussianParams.ReadParamsFromHdfs(paramsFilename, conf, k, d);
		
		//compute statistics
		Stats[] stat = new Stats[k];
		double[] p = PosteriorProbability.compute_p(params, x); //compute posterior probability
		for (int i = 0; i < k; i++) {
			stat[i] = new Stats(p[i], params[i].getMu(), x); //compute statistics
			context.write(new IntWritable(i), stat[i]);
			// GaussianParams test = new GaussianParams(d);
			// test.setW(stat[i].getS0());
			// test.setMu(stat[i].getS1());
			// test.setSigma(stat[i].getS2());
			// System.out.println(String.format("\nW: %s\nmu: %s\nsigma: %s", test.getWasString(),
			// test.getMuAsString(), test.getSigmaAsString()));
		}
	}

}
