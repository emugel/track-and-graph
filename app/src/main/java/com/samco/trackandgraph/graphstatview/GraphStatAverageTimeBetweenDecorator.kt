/*
 * This file is part of Track & Graph
 *
 * Track & Graph is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Track & Graph is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Track & Graph.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.samco.trackandgraph.graphstatview

import android.content.Context
import android.view.View
import com.samco.trackandgraph.R
import com.samco.trackandgraph.database.AverageTimeBetweenStat
import com.samco.trackandgraph.database.FeatureType
import com.samco.trackandgraph.database.GraphOrStat
import com.samco.trackandgraph.database.TrackAndGraphDatabaseDao
import com.samco.trackandgraph.databinding.GraphStatViewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.threeten.bp.Duration
import org.threeten.bp.OffsetDateTime

class GraphStatAverageTimeBetweenDecorator(
    private val graphOrStat: GraphOrStat,
    private val timeBetweenStat: AverageTimeBetweenStat,
    private val onSampledDataCallback: SampleDataCallback?
) : IGraphStatViewDecorator {
    private var binding: GraphStatViewBinding? = null
    private var context: Context? = null
    private var dataSource: TrackAndGraphDatabaseDao? = null

    override suspend fun decorate(view: IDecoratableGraphStatView) {
        binding = view.getBinding()
        context = view.getContext()
        dataSource = view.getDataSource()

        binding!!.statMessage.visibility = View.INVISIBLE
        initHeader(binding, graphOrStat)
        initAverageTimeBetweenStatBody()
    }

    override fun setTimeMarker(time: OffsetDateTime) { }

    private suspend fun initAverageTimeBetweenStatBody() {
        binding!!.progressBar.visibility = View.VISIBLE
        val dataPoints = getRelevantDataPoints()
        if (dataPoints.size < 2) {
            onSampledDataCallback?.invoke(emptyList())
            throw GraphStatInitException(R.string.graph_stat_view_not_enough_data_stat)
        } else {
            val totalMillis =
                Duration.between(dataPoints.first().timestamp, dataPoints.last().timestamp)
                    .toMillis().toDouble()
            val averageMillis = totalMillis / dataPoints.size.toDouble()
            setAverageTimeBetweenStatText(averageMillis)
            onSampledDataCallback?.invoke(dataPoints)
        }

        binding!!.statMessage.visibility = View.VISIBLE
        binding!!.progressBar.visibility = View.GONE
    }

    private suspend fun getRelevantDataPoints() = withContext(Dispatchers.IO) {
        val feature = dataSource!!.getFeatureById(timeBetweenStat.featureId)
        val endDate = graphOrStat.endDate ?: OffsetDateTime.now()
        val startDate = timeBetweenStat.duration?.let { endDate.minus(it) } ?: OffsetDateTime.MIN
        when (feature.featureType) {
            FeatureType.CONTINUOUS -> {
                dataSource!!.getDataPointsBetweenInTimeRange(
                    feature.id,
                    timeBetweenStat.fromValue,
                    timeBetweenStat.toValue,
                    startDate,
                    endDate
                )
            }
            FeatureType.DISCRETE -> {
                dataSource!!.getDataPointsWithValueInTimeRange(
                    feature.id,
                    timeBetweenStat.discreteValues,
                    startDate,
                    endDate
                )
            }
        }
    }

    private fun setAverageTimeBetweenStatText(millis: Double) {
        val days = "%.1f".format((millis / 86400000).toFloat())
        binding!!.statMessage.text = "$days ${context!!.getString(R.string.days)}"
    }
}