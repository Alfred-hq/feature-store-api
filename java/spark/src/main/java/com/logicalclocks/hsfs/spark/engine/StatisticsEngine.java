/*
 *  Copyright (c) 2020-2023. Hopsworks AB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 *  See the License for the specific language governing permissions and limitations under the License.
 *
 */

package com.logicalclocks.hsfs.spark.engine;

import com.logicalclocks.hsfs.EntityEndpointType;
import com.logicalclocks.hsfs.FeatureStoreException;
import com.logicalclocks.hsfs.Split;
import com.logicalclocks.hsfs.FeatureGroupBase;
import com.logicalclocks.hsfs.metadata.SplitStatistics;
import com.logicalclocks.hsfs.metadata.Statistics;
import com.logicalclocks.hsfs.metadata.StatisticsApi;

import com.logicalclocks.hsfs.spark.FeatureView;
import com.logicalclocks.hsfs.spark.TrainingDataset;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class StatisticsEngine {

  private StatisticsApi statisticsApi;

  private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsEngine.class);

  public StatisticsEngine(EntityEndpointType entityType) {
    this.statisticsApi = new StatisticsApi(entityType);
  }

  public Statistics computeStatistics(TrainingDataset trainingDataset, Dataset<Row> dataFrame)
      throws IOException, FeatureStoreException {
    Optional<Statistics> statistics = computeStatistics(dataFrame,
        trainingDataset.getStatisticsConfig().getColumns(),
        trainingDataset.getStatisticsConfig().getHistograms(),
        trainingDataset.getStatisticsConfig().getCorrelations(),
        trainingDataset.getStatisticsConfig().getExactUniqueness(),
        null);

    if (statistics.isPresent()) {
      return statisticsApi.post(trainingDataset, statistics.get());
    }

    return null;
  }

  public Statistics computeStatistics(FeatureView featureView, TrainingDataset trainingDataset, Dataset<Row> dataFrame)
      throws FeatureStoreException, IOException {
    Optional<Statistics> statistics = computeStatistics(dataFrame,
        trainingDataset.getStatisticsConfig().getColumns(),
        trainingDataset.getStatisticsConfig().getHistograms(),
        trainingDataset.getStatisticsConfig().getCorrelations(),
        trainingDataset.getStatisticsConfig().getExactUniqueness(),
        null);

    if (statistics.isPresent()) {
      return statisticsApi.post(featureView, trainingDataset.getVersion(), statistics.get());
    }

    return null;
  }

  public Statistics computeStatistics(FeatureGroupBase featureGroup, Dataset<Row> dataFrame, Long commitId)
      throws FeatureStoreException, IOException {

    Optional<Statistics> statistics = computeStatistics(dataFrame,
        featureGroup.getStatisticsConfig().getColumns(),
        featureGroup.getStatisticsConfig().getHistograms(),
        featureGroup.getStatisticsConfig().getCorrelations(),
        featureGroup.getStatisticsConfig().getExactUniqueness(),
        commitId);

    if (statistics.isPresent()) {
      return statisticsApi.post(featureGroup, statistics.get());
    }

    return null;
  }

  private Optional<Statistics> computeStatistics(Dataset<Row> dataFrame, List<String> statisticColumns,
                                                 Boolean histograms,
                                                 Boolean correlations, Boolean exactUniqueness, Long commitId) {
    if (dataFrame.isEmpty()) {
      LOGGER.warn("There is no data in the entity that you are trying to compute statistics for. A "
          + "possible cause might be that you inserted only data to the online storage of a feature group.");
      return Optional.empty();
    }
    Long commitTime = Timestamp.valueOf(LocalDateTime.now()).getTime();
    String content = SparkEngine.getInstance().profile(dataFrame, statisticColumns, histograms, correlations,
                                                       exactUniqueness);
    return Optional.of(new Statistics(commitTime, commitId, content, null));
  }

  public Statistics registerSplitStatistics(TrainingDataset trainingDataset)
      throws FeatureStoreException, IOException {
    Statistics statistics = getSplitStatistics(trainingDataset);
    return statisticsApi.post(trainingDataset, statistics);
  }

  public Statistics registerSplitStatistics(FeatureView featureView, TrainingDataset trainingDataset,
      Map<String, Dataset<Row>> splitDatasets)
      throws FeatureStoreException, IOException {
    Statistics statistics = getSplitStatistics(trainingDataset, splitDatasets);
    return statisticsApi.post(featureView, trainingDataset.getVersion(), statistics);
  }

  public Statistics getSplitStatistics(TrainingDataset trainingDataset)
      throws FeatureStoreException, IOException {
    List<SplitStatistics> splitStatistics = new ArrayList<>();
    for (Split split : trainingDataset.getSplits()) {
      Optional<Statistics> statistics = computeStatistics(trainingDataset.read(split.getName()),
          trainingDataset.getStatisticsConfig().getColumns(),
          trainingDataset.getStatisticsConfig().getHistograms(),
          trainingDataset.getStatisticsConfig().getCorrelations(),
          trainingDataset.getStatisticsConfig().getExactUniqueness(),
          null);

      if  (statistics.isPresent()) {
        splitStatistics.add(new SplitStatistics(split.getName(), statistics.get().getContent()));
      }
    }
    Long commitTime = Timestamp.valueOf(LocalDateTime.now()).getTime();
    return new Statistics(commitTime, null, null, splitStatistics);
  }

  public Statistics getSplitStatistics(TrainingDataset trainingDataset, Map<String, Dataset<Row>> splitDatasets) {
    List<SplitStatistics> splitStatistics = new ArrayList<>();
    for (Map.Entry<String, Dataset<Row>> entry : splitDatasets.entrySet()) {
      Optional<Statistics> statistics = computeStatistics(entry.getValue(),
          trainingDataset.getStatisticsConfig().getColumns(),
          trainingDataset.getStatisticsConfig().getHistograms(),
          trainingDataset.getStatisticsConfig().getCorrelations(),
          trainingDataset.getStatisticsConfig().getExactUniqueness(),
          null);

      if (statistics.isPresent()) {
        splitStatistics.add(new SplitStatistics(entry.getKey(), statistics.get().getContent()));
      } else {
        splitStatistics.add(new SplitStatistics(entry.getKey(), "{}"));
      }
    }
    Long commitTime = Timestamp.valueOf(LocalDateTime.now()).getTime();
    return new Statistics(commitTime, null, null, splitStatistics);
  }

  public Statistics get(FeatureGroupBase featureGroup, String commitTime) throws FeatureStoreException, IOException {
    return statisticsApi.get(featureGroup, commitTime);
  }

  public Statistics get(TrainingDataset trainingDataset, String commitTime) throws FeatureStoreException, IOException {
    return statisticsApi.get(trainingDataset, commitTime);
  }

  public Statistics getLast(FeatureGroupBase featureGroup) throws FeatureStoreException, IOException {
    return statisticsApi.getLast(featureGroup);
  }

  public Statistics getLast(TrainingDataset trainingDataset) throws FeatureStoreException, IOException {
    return statisticsApi.getLast(trainingDataset);
  }
}
