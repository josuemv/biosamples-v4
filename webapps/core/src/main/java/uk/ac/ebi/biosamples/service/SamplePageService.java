package uk.ac.ebi.biosamples.service;

import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import uk.ac.ebi.biosamples.model.CurationLink;
import uk.ac.ebi.biosamples.model.Sample;
import uk.ac.ebi.biosamples.mongo.model.MongoCurationLink;
import uk.ac.ebi.biosamples.mongo.model.MongoSample;
import uk.ac.ebi.biosamples.mongo.repo.MongoCurationLinkRepository;
import uk.ac.ebi.biosamples.mongo.repo.MongoSampleRepository;
import uk.ac.ebi.biosamples.mongo.service.MongoSampleToSampleConverter;
import uk.ac.ebi.biosamples.solr.model.SolrSample;
import uk.ac.ebi.biosamples.solr.service.SolrSampleService;

/**
 * Service layer business logic for centralising repository access and
 * conversions between different controller. Use this instead of linking to
 * repositories directly.
 * 
 * @author faulcon
 *
 */
@Service
public class SamplePageService {

	private Logger log = LoggerFactory.getLogger(getClass());

	@Autowired
	private MongoSampleRepository mongoSampleRepository;
	@Autowired
	private MongoCurationLinkRepository mongoCurationLinkRepository;
	
	
	//TODO use a ConversionService to manage all these
	@Autowired
	private MongoSampleToSampleConverter mongoSampleToSampleConverter;

	@Autowired
	private SampleReadService sampleService;
	
	@Autowired
	private SolrSampleService solrSampleService;
	

	//@Cacheable(cacheNames=WebappProperties.getSamplesByText, sync=true)
	public Page<Sample> getSamplesByText(String text, MultiValueMap<String,String> filters, 
			LocalDateTime after, LocalDateTime before, Pageable pageable) {		
		Page<SolrSample> pageSolrSample = solrSampleService.fetchSolrSampleByText(text, filters, after, before, pageable);		
		//for each result fetch the stored version and add e.g. inverse relationships		
		//stream process each into a sample *in parallel*
		Page<Sample> pageSample = new PageImpl<>(StreamSupport.stream(pageSolrSample.spliterator(), true)
					.map(ss->sampleService.fetch(ss.getAccession()).get()).collect(Collectors.toList()), 
				pageable,pageSolrSample.getTotalElements()); 
		return pageSample;
	}	

	public Page<Sample> getSamplesOfExternalReference(String urlHash, Pageable pageable) {
		Page<MongoSample> pageMongoSample = mongoSampleRepository.findByExternalReferences_Hash(urlHash, pageable);		
		//convert them into a state to return
		Page<Sample> pageSample = pageMongoSample.map(mongoSampleToSampleConverter);
		return pageSample;
	}

	public Page<Sample> getSamplesOfCuration(String hash, Pageable pageable) {
		Page<MongoCurationLink> accession = mongoCurationLinkRepository.findByCurationHash(hash, pageable);
		//stream process each into a sample *in parallel*
		Page<Sample> pageSample = new PageImpl<>(StreamSupport.stream(accession.spliterator(), true)
					.map(mcl->sampleService.fetch(mcl.getSample()).get()).collect(Collectors.toList()), 
				pageable, accession.getTotalElements());			
		return pageSample;
	}
}
