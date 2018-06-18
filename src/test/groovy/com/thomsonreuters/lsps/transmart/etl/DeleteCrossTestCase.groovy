package com.thomsonreuters.lsps.transmart.etl

import com.thomsonreuters.lsps.db.core.DatabaseType
import com.thomsonreuters.lsps.transmart.Fixtures
import com.thomsonreuters.lsps.transmart.fixtures.ClinicalData
import com.thomsonreuters.lsps.transmart.fixtures.Study
import spock.lang.Specification

import static com.thomsonreuters.lsps.transmart.Fixtures.studyDir
import static org.hamcrest.CoreMatchers.equalTo
import static org.junit.Assert.assertThat
import static com.thomsonreuters.lsps.transmart.Fixtures.*
import static com.thomsonreuters.lsps.transmart.etl.matchers.SqlMatchers.*
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.is
import static org.hamcrest.core.IsNot.not
import static org.junit.Assert.*


class DeleteCrossTestCase extends Specification implements ConfigAwareTestCase {

    private ClinicalDataProcessor _clinicalProcessor
    private DeleteCrossProcessor _deleteCrossProcessor
    private DeleteDataProcessor _deleteDataProcessor

    DeleteDataProcessor getDeleteDataProcessor() {
        _deleteDataProcessor ?: (_deleteDataProcessor = new DeleteDataProcessor(config))
    }

    DeleteCrossProcessor getDeleteCrossProcessor() {
        _deleteCrossProcessor ?: (_deleteCrossProcessor = new DeleteCrossProcessor(config))
    }

    ClinicalDataProcessor getClinicalProcessor() {
        _clinicalProcessor ?: (_clinicalProcessor = new ClinicalDataProcessor(config))
    }

    void setup() {
        ConfigAwareTestCase.super.setUp()
        runScript('I2B2_DELETE_CROSS_DATA.sql')
    }

    def 'it should not delete cross node from i2b2 schema'() {
        given:
        def studyConceptId = "GSECONCEPTCD"
        def studyConceptName = 'Test Data With Concept_cd'

        Study.deleteById(config, studyConceptId)
        clinicalProcessor.process(
                new File(studyDir(studyConceptName, studyConceptId), "ClinicalDataToUpload").toPath(),
                [name: studyConceptName, node: "Test Studies\\${studyConceptName}".toString()])

        when:
        def data = [
                path            : "\\Vital\\",
                isDeleteConcepts: false
        ]

        def operation = deleteCrossProcessor.process(data)

        then:
        assertThat("Should check exists observation fact", operation, equalTo(false))
        assertThatCrossNodeDelete(data.path, false)
        assertThatConceptDelete(data.path, false)
        assertThatConceptDelete(data.path + "Node 1\\Node 2\\Flag\\", false)
    }

    def 'it should delete cross node from i2b2 schema'() {
        given:
        def studyConceptId = "GSECONCEPTCD"
        def studyConceptName = 'Test Data With Concept_cd'

        Study.deleteById(config, studyConceptId)
        clinicalProcessor.process(
                new File(studyDir(studyConceptName, studyConceptId), "ClinicalDataToUpload").toPath(),
                [name: studyConceptName, node: "Test Studies\\${studyConceptName}".toString()])

        when:
        // Remove study before removing cross node
        deleteDataProcessor.process([id: studyConceptId])

        def data = [
                path            : "\\Vital\\",
                isDeleteConcepts: false
        ]

        def operation = deleteCrossProcessor.process(data)

        then:
        assertTrue(operation)

        assertThatCrossNodeDelete(data.path)
        assertThatCrossNodeDelete(data.path + "Node 1\\Node 2\\Flag\\")
        assertThatConceptDelete(data.path, false)
        assertThatConceptDelete(data.path + "Node 1\\Node 2\\Flag\\", false)
    }

    def 'it should delete cross node from i2b2 schema and concept dimension'() {
        given:
        def studyConceptId = "GSECONCEPTCD"
        def studyConceptName = 'Test Data With Concept_cd'

        Study.deleteById(config, studyConceptId)
        clinicalProcessor.process(
                new File(studyDir(studyConceptName, studyConceptId), "ClinicalDataToUpload").toPath(),
                [name: studyConceptName, node: "Test Studies\\${studyConceptName}".toString()])

        when:
        // Remove study before removing cross node
        deleteDataProcessor.process([id: studyConceptId])

        def data = [
                path            : "\\Vital\\",
                isDeleteConcepts: true
        ]

        def operation = deleteCrossProcessor.process(data)

        then:
        assertTrue(operation)

        assertThatCrossNodeDelete(data.path)
        assertThatCrossNodeDelete(data.path + "Node 1\\Node 2\\Flag\\")
        assertThatConceptDelete(data.path)
        assertThatConceptDelete(data.path + "Node 1\\Node 2\\Flag\\")
    }

    def 'it should delete concepts after deleting tree and study'(){
        given:
        def studyConceptId = "GSECONCEPTCD"
        def studyConceptName = 'Test Data With Concept_cd'

        Study.deleteById(config, studyConceptId)
        clinicalProcessor.process(
                new File(studyDir(studyConceptName, studyConceptId), "ClinicalDataToUpload").toPath(),
                [name: studyConceptName, node: "Test Studies\\${studyConceptName}".toString()])

        // Remove study before removing cross node
        deleteDataProcessor.process([id: studyConceptId])

        // Remove cross tree
        def data = [
                path            : "\\Vital\\",
                isDeleteConcepts: false
        ]

        deleteCrossProcessor.process(data)
        assertThatConceptDelete(data.path, false)
        assertThatConceptDelete(data.path + "Node 1\\Node 2\\Flag\\", false)

        when:
        // Remove cross tree
        data = [
                path            : "\\Vital\\",
                isDeleteConcepts: true
        ]
        def operation = deleteCrossProcessor.process(data)

        then:
        assertTrue(operation)

        assertThatCrossNodeDelete(data.path)
        assertThatCrossNodeDelete(data.path + "Node 1\\Node 2\\Flag\\")
        assertThatConceptDelete(data.path)
        assertThatConceptDelete(data.path + "Node 1\\Node 2\\Flag\\")
    }

    def 'it should check throw exception then cross node delete by path'(){

    }

    void assertThatCrossNodeDelete(String path, isDelete = true) {
        def i2b2Count = sql.firstRow('select count(*) from i2b2metadata.i2b2 where c_fullname = ?', path)
        isDelete ?
                assertEquals('Row deleted from i2b2 table', 0, (Integer) i2b2Count[0]) :
                assertEquals('Row didn\'t delete from i2b2 table', 1, (Integer) i2b2Count[0])

    }

    void assertThatConceptDelete(String path, isDelete = true) {
        def concept = sql.firstRow('select count(*) from i2b2demodata.concept_dimension where concept_path = ?', path)
        isDelete ?
                assertEquals('Row deleted from concept_dimension table', 0, (Integer) concept[0]) :
                assertEquals('Row didn\'t delete from concept_dimension table', 1, (Integer) concept[0])

    }
}
