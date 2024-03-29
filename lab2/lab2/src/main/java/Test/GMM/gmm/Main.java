package Test.GMM.gmm;

import Test.GMM.mapreduce.GmmCombiner;
import Test.GMM.mapreduce.GmmMapper;
import Test.GMM.mapreduce.GmmReducer;
import Test.GMM.utils.DimensionsReader;
import Test.GMM.utils.GaussianParams;
import Test.GMM.utils.Stats;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;


public class Main {
    private static final int MAX_ITERATIONS = 300;
    private static final double EPSILON = 0.05;

    public static void main(String[] args) throws Exception {

        String inputFilename = "x.txt";
        String outputFolder = "output";
        String paramsFilename = "params.txt";
        String dimensionsFilename = "dimensions.txt";

        String k, d;
        Configuration conf = new Configuration();
        String[] dimensions = DimensionsReader.ReadDimensionsFromHdfs(dimensionsFilename, conf);
        k = dimensions[0];
        d = dimensions[1];

        conf.setStrings("initParams", paramsFilename);
        conf.setInt("k", Integer.parseInt(k));

        // iterations
        boolean toBeContinued = true;
        int nIternation = 0;
        System.out.println("Convergence threshold: " + EPSILON);
        System.out.println("Max number of iterations: " + MAX_ITERATIONS);
        long startTime = System.currentTimeMillis();
        while (toBeContinued && nIternation < MAX_ITERATIONS) {
            System.out.println("\n------------------------------ITERATION #" + nIternation + "------------------------------");
            Job job = Job.getInstance(conf, "gmm");
            job.setJarByClass(Main.class);

            job.setOutputKeyClass(IntWritable.class);
            job.setOutputValueClass(Stats.class);

            job.setMapperClass(GmmMapper.class);

            job.setMapOutputValueClass(Stats.class);
            job.setCombinerClass(GmmCombiner.class);
            job.setReducerClass(GmmReducer.class);
            job.setNumReduceTasks(Integer.parseInt(k));

            job.setInputFormatClass(TextInputFormat.class);
            job.setOutputFormatClass(TextOutputFormat.class);

            FileInputFormat.addInputPath(job, new Path(inputFilename));
            FileOutputFormat.setOutputPath(job, new Path(outputFolder));

            job.waitForCompletion(true);

            // eval escape condition
            GaussianParams[] oldParams = GaussianParams.ReadParamsFromHdfs(paramsFilename, conf, Integer.parseInt(k), Integer.parseInt(d));
//			System.out.println("\n---OLD---");
//			for (int i = 0; i < oldParams.length; i++) {
//				String output = String.format("%s\n%s\n%s", oldParams[i].getWasString(), oldParams[i].getMuAsString(), oldParams[i].getSigmaSqrAsString());
//				System.out.println("Gaussian #" + i + "\n" + output);
//			}


            FileSystem fs = FileSystem.get(conf);
            fs.delete(new Path(paramsFilename), true);

//			FileUtil.copyMerge(fs, new Path(outputFolder), fs, new Path(paramsFilename), false, conf, null);

            GaussianParams[] newParams = GaussianParams.ReadParamsFromHdfs(paramsFilename, conf, Integer.parseInt(k), Integer.parseInt(d));
//			System.out.println("\n---NEW---");
//			for (int i = 0; i < newParams.length; i++) {
//				String output = String.format("%s\n%s\n%s", newParams[i].getWasString(), newParams[i].getMuAsString(), newParams[i].getSigmaSqrAsString());
//				System.out.println("Gaussian #" + i + "\n" + output);
//			}

            toBeContinued = GaussianParams.evaluateStop(oldParams, newParams, EPSILON);

            fs.delete(new Path(outputFolder), true);

            nIternation++;
        }
        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Program exited after " + nIternation + " iterations executed in " + durationFormatter(duration) + ". \nReason:\n \t-enough approximation: " + !toBeContinued + "\n \t-max iterations exceeded: " + !(nIternation < MAX_ITERATIONS));
    }

    private static String durationFormatter(long ms) {
        final int SECOND = 1000;
        final int MINUTE = 60 * SECOND;
        final int HOUR = 60 * MINUTE;
        final int DAY = 24 * HOUR;

        StringBuffer text = new StringBuffer("");
        if (ms > DAY) {
            text.append(ms / DAY).append(" days ");
            ms %= DAY;
        }
        if (ms > HOUR) {
            text.append(ms / HOUR).append(" hours ");
            ms %= HOUR;
        }
        if (ms > MINUTE) {
            text.append(ms / MINUTE).append(" minutes ");
            ms %= MINUTE;
        }
        if (ms > SECOND) {
            text.append(ms / SECOND).append(" seconds ");
            ms %= SECOND;
        }
        text.append(ms + " ms");
        return text.toString();
    }

}
