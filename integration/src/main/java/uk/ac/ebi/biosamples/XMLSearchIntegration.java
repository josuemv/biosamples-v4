package uk.ac.ebi.biosamples;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.hateoas.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.ac.ebi.biosamples.client.BioSamplesClient;
import uk.ac.ebi.biosamples.model.Attribute;
import uk.ac.ebi.biosamples.model.Sample;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

@Component
@Profile({"default", "test"})
public class XMLSearchIntegration extends AbstractIntegration {
    
    private final RestTemplate restTemplate;
    private final IntegrationProperties integrationProperties;

    public XMLSearchIntegration(BioSamplesClient client,
                                RestTemplateBuilder restTemplateBuilder,
                                IntegrationProperties integrationProperties) {
        super(client);
        this.restTemplate = restTemplateBuilder.build();
        this.integrationProperties = integrationProperties;

    }

    @Override
    protected void phaseOne() {
        final Sample test1 = getSampleXMLTest1();

        Optional<Resource<Sample>> optional = client.fetchSampleResource(test1.getAccession());
        if (optional.isPresent()) {
            throw new RuntimeException("Found existing "+test1.getAccession());
        }

        Resource<Sample> resource = client.persistSampleResource(test1);
        if (!test1.equals(resource.getContent())) {
            throw new RuntimeException("Expected response to equal submission");
        }
    }

    @Override
    protected void phaseTwo() {
        Sample test1 = getSampleXMLTest1();

        Optional<Resource<Sample>> optional = client.fetchSampleResource(test1.getAccession());
        if (!optional.isPresent()) {
            throw new RuntimeException("Expected sample not found "+test1.getAccession());
        }

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUri(integrationProperties.getBiosampleLegaxyXmlUri());
        uriBuilder.pathSegment("samples", test1.getAccession());
        //TODO FINISH THIS
        ResponseEntity<Sample> responseEntity = restTemplate.getForEntity(uriBuilder.build().toUri(), Sample.class);
        if (!responseEntity.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Expected sample not found in the xml legacy "+test1.getAccession());
        }

        assert responseEntity.getBody().equals(test1);
        /*
        try {

        } catch(HttpClientErrorException ex) {

        }
        */


    }

    @Override
    protected void phaseThree() {

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUri(integrationProperties.getBiosampleLegaxyXmlUri());

        UriComponentsBuilder testBadRequest= uriBuilder.cloneBuilder();
        testBadRequest.pathSegment("samples");

        // Accept text/xml header
        HttpHeaders xmlHeaders = new HttpHeaders();
        xmlHeaders.setAccept(Collections.singletonList(MediaType.TEXT_XML));

        // Accept application/json header
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        try {
            restTemplate.exchange(testBadRequest.toUriString(),
                    HttpMethod.GET,
                    new HttpEntity<>(xmlHeaders),
                    Sample.class);
        } catch(HttpClientErrorException ex) {
            boolean expectedResponse = ex.getStatusCode().is4xxClientError();
            expectedResponse = expectedResponse && ex.getRawStatusCode() == 400;
            expectedResponse = expectedResponse && ex.getResponseHeaders().getContentType().equals(MediaType.TEXT_XML);

            if (!expectedResponse) {
                throw ex;
            }
        }

        // Check application/json request
        try {
            restTemplate.exchange(testBadRequest.toUriString(),
                    HttpMethod.GET,
                    new HttpEntity<>(jsonHeaders),
                    Sample.class);
        } catch(HttpClientErrorException ex) {
            boolean expectedResponse = ex.getStatusCode().is4xxClientError();
            expectedResponse = expectedResponse && ex.getRawStatusCode() == 406;
            expectedResponse = expectedResponse && ex.getResponseHeaders().getContentType().includes(MediaType.APPLICATION_JSON);
            if (!expectedResponse) {
                throw ex;
            }
        }

        UriComponentsBuilder testProperRequest= uriBuilder.cloneBuilder();
        testProperRequest.pathSegment("samples");
        testProperRequest.queryParam("query", "test");


        ResponseEntity<String> responseEntity = restTemplate.exchange(
                    testProperRequest.toUriString(),
                    HttpMethod.GET,
                    new HttpEntity<>(xmlHeaders),
                    String.class);
        if (!responseEntity.getStatusCode().is2xxSuccessful() ||
                responseEntity.getBody() == null) {
            throw new RuntimeException("Unexpected result for pagination query in the legacy xml");
        }

    }

    @Override
    protected void phaseFour() {

    }

    @Override
    protected void phaseFive() {

    }

    private Sample getSampleXMLTest1() {
		String name = "Test XML Sample";
		String accession = "SAMEAXML123123";
		LocalDateTime update = LocalDateTime.of(LocalDate.of(2016, 5, 5), LocalTime.of(11, 36, 57, 0));
		LocalDateTime release = LocalDateTime.of(LocalDate.of(2116, 4, 1), LocalTime.of(11, 36, 57, 0));

		SortedSet<Attribute> attributes = new TreeSet<>();
		attributes.add(
			Attribute.build("organism", "Homo sapiens", "http://purl.obolibrary.org/obo/NCBITaxon_9606", null));

		return Sample.build(name, accession, release, update, attributes, new TreeSet<>(), new TreeSet<>());
	}}
