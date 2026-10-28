package moe.antimony.hoshi.features.study

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import moe.antimony.hoshi.dictionary.DictionaryRepository

/** Accesses the production graph for platform-level verification without replacing repositories. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface StudyEntryPoint {
    fun notebook(): StudyRepository
    fun dictionary(): DictionaryRepository
}
