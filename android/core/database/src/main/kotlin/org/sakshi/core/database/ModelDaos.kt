package org.sakshi.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Access to model versions. Rows are never updated or deleted. */
@Dao
public abstract class ModelVersionDao {
    @Insert
    public abstract suspend fun insert(model: ModelVersionEntity)

    @Query("SELECT * FROM model_version WHERE id = :id")
    public abstract suspend fun get(id: String): ModelVersionEntity?

    @Query("SELECT * FROM model_version ORDER BY name, id")
    public abstract suspend fun all(): List<ModelVersionEntity>
}

/** Access to producer-to-schema label mappings. Rows are never updated or deleted. */
@Dao
public abstract class LabelMappingDao {
    @Insert
    public abstract suspend fun insert(mapping: LabelMappingEntity)

    /** The mapping with the highest version for a producer label, or null when none exists. */
    @Query(
        "SELECT * FROM label_mapping WHERE producer = :producer AND producer_label = :producerLabel " +
            "ORDER BY mapping_version DESC LIMIT 1",
    )
    public abstract suspend fun latest(producer: String, producerLabel: String): LabelMappingEntity?

    @Query("SELECT * FROM label_mapping WHERE producer = :producer ORDER BY producer_label, mapping_version")
    public abstract suspend fun forProducer(producer: String): List<LabelMappingEntity>
}
