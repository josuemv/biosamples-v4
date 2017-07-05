package uk.ac.ebi.biosamples;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import uk.ac.ebi.biosamples.client.BioSamplesClient;
import uk.ac.ebi.biosamples.model.Attribute;
import uk.ac.ebi.biosamples.model.ExternalReference;
import uk.ac.ebi.biosamples.model.Relationship;
import uk.ac.ebi.biosamples.model.Sample;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

@Component
@Order(2)
@Profile({"default", "rest"})
public class RestIntegration extends AbstractIntegration {

	private Logger log = LoggerFactory.getLogger(this.getClass());
	private final RestTemplate restTemplate;
	
	public RestIntegration(BioSamplesClient client, RestTemplateBuilder restTemplateBuilder) {
		super(client);
		this.restTemplate = restTemplateBuilder.build();
		
	}
	
	@Override
	protected void phaseOne() {
		Sample sampleTest1 = getSampleTest1();
		
		// get and check that nothing exists already
		Optional<Resource<Sample>> optional = client.fetchSampleResource(sampleTest1.getAccession());
		if (optional.isPresent()) {
			throw new RuntimeException("Found existing "+sampleTest1.getAccession());
		}
		
		// put a sample
		Resource<Sample> resource = client.persistSampleResource(sampleTest1);
		if (!sampleTest1.equals(resource.getContent())) {
			throw new RuntimeException("Expected response to equal submission");
		}
	}
	
	@Override
	protected void phaseTwo() {
		Sample sampleTest1 = getSampleTest1();
		
		// get to check it worked
		Optional<Resource<Sample>> optional = client.fetchSampleResource(sampleTest1.getAccession());
		if (!optional.isPresent()) {
			throw new RuntimeException("No existing "+sampleTest1.getAccession());
		}
		
		checkIfModifiedSince(optional.get());
		checkIfMatch(optional.get());

		// put a version that is private
		sampleTest1 = Sample.build(sampleTest1.getName(), sampleTest1.getAccession(),
				LocalDateTime.of(LocalDate.of(2116, 4, 1), LocalTime.of(11, 36, 57, 0)), sampleTest1.getUpdate(),
				sampleTest1.getCharacteristics(), sampleTest1.getRelationships(), sampleTest1.getExternalReferences());
		
		Resource<Sample> resource = client.persistSampleResource(sampleTest1);
		if (!sampleTest1.equals(resource.getContent())) {
			throw new RuntimeException("Expected response to equal submission");
		}
		
		//TODO check If-Unmodified-Since
		//TODO check If-None-Match
	}
	
	@Override
	protected void phaseThree() {
		Sample sampleTest1 = getSampleTest1();
		Sample sampleTest2 = getSampleTest2();
		
		// check that it is private again
		Optional<Resource<Sample>> optional = client.fetchSampleResource(sampleTest1.getAccession());
		if (optional.isPresent()) {
			throw new RuntimeException("Found existing "+sampleTest1.getAccession());
		}
		
		//put the second sample in
		Resource<Sample> resource = client.persistSampleResource(sampleTest2);
		if (!sampleTest2.equals(resource.getContent())) {
			throw new RuntimeException("Expected response to equal submission");
		}		
	}
	
	@Override
	protected void phaseFour() {	
		Sample sampleTest1 = getSampleTest1();
		Sample sampleTest2 = getSampleTest2();
		//at this point, the inverse relationship should have been added
		
		sampleTest2 = Sample.build(sampleTest2.getName(), sampleTest2.getAccession(),
				sampleTest2.getRelease(), sampleTest2.getUpdate(),
				sampleTest2.getCharacteristics(), sampleTest1.getRelationships(), sampleTest2.getExternalReferences());
		
		//check that it has the additional relationship added
		// get to check it worked
		Optional<Resource<Sample>> optional = client.fetchSampleResource(sampleTest2.getAccession());
		if (!optional.isPresent()) {
			throw new RuntimeException("No existing "+sampleTest2.getAccession());
		}
		Sample sampleTest2Rest = optional.get().getContent();
		
		//check utf -8
		if (!sampleTest2Rest.getCharacteristics().contains(Attribute.build("UTF-8 test", "αβ", null, null))) {
			throw new RuntimeException("Unable to find UTF-8 characters");
		}
		
		//now do another update to delete the relationship
		//might as well make it public now too
		sampleTest1 = Sample.build(sampleTest1.getName(), sampleTest1.getAccession(),
				LocalDateTime.of(LocalDate.of(2016, 4, 1), LocalTime.of(11, 36, 57, 0)), sampleTest1.getUpdate(),
				sampleTest1.getCharacteristics(), new TreeSet<>(), sampleTest1.getExternalReferences());
		Resource<Sample> resource = client.persistSampleResource(sampleTest1);
		if (!sampleTest1.equals(resource.getContent())) {
			throw new RuntimeException("Expected response to equal submission");
		}
		
	}
	
	@Override
	protected void phaseFive() {	
	}

	private void checkIfModifiedSince(Resource<Sample> sample) {
		HttpHeaders headers = new HttpHeaders();
		headers.setIfModifiedSince(0);
		ResponseEntity<Resource<Sample>> response = restTemplate.exchange(sample.getLink(Link.REL_SELF).getHref(), 
				HttpMethod.GET, new HttpEntity<Void>(headers), 
				new ParameterizedTypeReference<Resource<Sample>>(){});
		
		if (!response.getStatusCode().equals(HttpStatus.NOT_MODIFIED)) {
			throw new RuntimeException("Got something other than a 304 response");
		}
	}
	private void checkIfMatch(Resource<Sample> sample) {
		HttpHeaders headers = new HttpHeaders();
		headers.setIfNoneMatch("W/\""+sample.getContent().hashCode()+"\"");
		ResponseEntity<Resource<Sample>> response = restTemplate.exchange(sample.getLink(Link.REL_SELF).getHref(), 
				HttpMethod.GET, new HttpEntity<Void>(headers), 
				new ParameterizedTypeReference<Resource<Sample>>(){});
		
		if (!response.getStatusCode().equals(HttpStatus.NOT_MODIFIED)) {
			throw new RuntimeException("Got something other than a 304 response");
		}
	}
	
	
	private Sample getSampleTest1() {
		String name = "Test Sample";
		String accession = "TESTrest1";
		LocalDateTime update = LocalDateTime.of(LocalDate.of(2016, 5, 5), LocalTime.of(11, 36, 57, 0));
		LocalDateTime release = LocalDateTime.of(LocalDate.of(2016, 4, 1), LocalTime.of(11, 36, 57, 0));

		SortedSet<Attribute> attributes = new TreeSet<>();
		attributes.add(
			Attribute.build("organism", "Homo sapiens", "http://purl.obolibrary.org/obo/NCBITaxon_9606", null));
		attributes.add(Attribute.build("age", "3", null, "year"));
		attributes.add(Attribute.build("organism part", "lung", null, null));
		attributes.add(Attribute.build("organism part", "heart", null, null));

		SortedSet<Relationship> relationships = new TreeSet<>();
		relationships.add(Relationship.build("TESTrest1", "derived from", "TESTrest2"));
		
		SortedSet<ExternalReference> externalReferences = new TreeSet<>();
		externalReferences.add(ExternalReference.build("http://www.google.com"));

		return Sample.build(name, accession, release, update, attributes, relationships, externalReferences);
	}

	private Sample getSampleTest2() {
		String name = "Test Sample the second";
		String accession = "TESTrest2";
		LocalDateTime update = LocalDateTime.of(LocalDate.of(2016, 5, 5), LocalTime.of(11, 36, 57, 0));
		LocalDateTime release = LocalDateTime.of(LocalDate.of(2016, 4, 1), LocalTime.of(11, 36, 57, 0));

		SortedSet<Attribute> attributes = new TreeSet<>();
		attributes.add(
			Attribute.build("organism", "Homo sapiens", "http://purl.obolibrary.org/obo/NCBITaxon_9606", null));
		attributes.add(Attribute.build("UTF-8 test", "αβ", null, null));

		return Sample.build(name, accession, release, update, attributes, new TreeSet<>(), new TreeSet<>());
	}

}
