package uk.ac.ebi.biosamples.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.ObjectMapper;

import uk.ac.ebi.biosamples.model.Attribute;
import uk.ac.ebi.biosamples.model.Contact;
import uk.ac.ebi.biosamples.model.Organization;
import uk.ac.ebi.biosamples.model.Publication;
import uk.ac.ebi.biosamples.mongo.model.MongoExternalReference;
import uk.ac.ebi.biosamples.mongo.model.MongoRelationship;
import uk.ac.ebi.biosamples.mongo.model.MongoSample;

@RunWith(SpringRunner.class)
@JsonTest
public class MongoSerializationTest {

	private Logger log = LoggerFactory.getLogger(getClass());

	private JacksonTester<MongoSample> json;
	
    @Before
    public void setup() {
        ObjectMapper objectMapper = new ObjectMapper();
        JacksonTester.initFields(this, objectMapper);
    }

	private MongoSample getMongoSample() throws URISyntaxException {
		String name = "Test Sample";
		String accession = "TEST1";
		Instant update = Instant.parse("2016-05-05T11:36:57.00Z");
		Instant release = Instant.parse("2016-04-01T11:36:57.00Z");

		SortedSet<Attribute> attributes = new TreeSet<>();
		attributes.add(Attribute.build("organism", "Homo sapiens", Lists.newArrayList("http://purl.obolibrary.org/obo/NCBITaxon_9606"), null));
		attributes.add(Attribute.build("age", "3", Collections.emptyList(), "year"));
		attributes.add(Attribute.build("organism part", "lung"));
		attributes.add(Attribute.build("organism part", "heart"));
		
		SortedSet<MongoRelationship> relationships = new TreeSet<>();
		relationships.add(MongoRelationship.build("TEST1", "derived from", "TEST2"));
		
		SortedSet<MongoExternalReference> externalReferences = new TreeSet<>();
		externalReferences.add(MongoExternalReference.build("http://www.google.com"));

		SortedSet<Organization> organizations = new TreeSet<>();
//		organizations.add(Organization.build("Jo Bloggs Inc", "user", "help@jobloggs.com", "http://www.jobloggs.com"));
		organizations.add(new Organization.Builder()
				.name("Jo Bloggs Inc")
				.role("user")
				.email("help@jobloggs.com")
				.url("http://www.jobloggs.com")
				.build());

		SortedSet<Contact> contacts = new TreeSet<>();
//		contacts.add(Contact.build("Joe Bloggs","Jo Bloggs Inc", "http://www.jobloggs.com/joe"));
		contacts.add(new Contact.Builder()
				.firstName("Joe")
				.lastName("Bloggs")
                .name("Joe Bloggs")
				.role("Submitter")
				.email("jobloggs@joblogs.com")
//				.affiliation("Jo Bloggs Inc")
//				.url("http://www.jobloggs.com/joe")
				.build());

		SortedSet<Publication> publications = new TreeSet<>();
//		publications.add(Publication.build("10.1093/nar/gkt1081", "24265224"));
		publications.add(new Publication.Builder()
				.doi("10.1093/nar/gkt1081")
				.pubmed_id("24265224")
				.build());

		return MongoSample.build(name, accession, "foozit", release, update, 
				attributes, relationships, externalReferences, 
				organizations, contacts, publications);
	}

	@Test
	public void testSerialize() throws Exception {
		MongoSample details = getMongoSample();

		System.out.println(this.json.write(details).getJson());

		// Use JSON path based assertions
		assertThat(this.json.write(details)).hasJsonPathStringValue("@.accession");
		assertThat(this.json.write(details)).extractingJsonPathStringValue("@.accession").isEqualTo("TEST1");

		// Assert against a `.json` file in the same package as the test
		log.info("testSerialize() "+this.json.write(details).getJson());
		assertThat(this.json.write(details)).isEqualToJson("/TEST1.json");
	}

	@Test
	public void testDeserialize() throws Exception {
		// Use JSON path based assertions
		assertThat(this.json.readObject("/TEST1.json").getName()).isEqualTo("Test Sample");
		assertThat(this.json.readObject("/TEST1.json").getAccession()).isEqualTo("TEST1");
		// Assert against a `.json` file
		assertThat(this.json.readObject("/TEST1.json")).isEqualTo(getMongoSample());
	}
	
	@Configuration
	public static class TestConfig {
		
	}

}
