package uk.ac.ebi.biosamples.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import uk.ac.ebi.biosamples.model.ExternalReference;
import uk.ac.ebi.biosamples.model.ExternalReferenceLink;
import uk.ac.ebi.biosamples.neo.model.NeoExternalReference;
import uk.ac.ebi.biosamples.neo.model.NeoExternalReferenceLink;
import uk.ac.ebi.biosamples.neo.repo.NeoExternalReferenceLinkRepository;
import uk.ac.ebi.biosamples.neo.repo.NeoExternalReferenceRepository;
import uk.ac.ebi.biosamples.neo.service.modelconverter.NeoExternalReferenceLinkToExternalReferenceLinkConverter;
import uk.ac.ebi.biosamples.neo.service.modelconverter.NeoExternalReferenceToExternalReferenceConverter;

@Service
public class ExternalReferenceService {

	@Autowired
	private NeoExternalReferenceRepository neoExternalReferenceRepository;
	@Autowired
	private NeoExternalReferenceLinkRepository neoExternalReferenceLinkRepository;
	
	@Autowired
	private NeoExternalReferenceToExternalReferenceConverter neoExternalReferenceToExternalReferenceConverter;
	@Autowired
	private NeoExternalReferenceLinkToExternalReferenceLinkConverter neoExternalReferenceLinkToExternalReferenceLinkConverter;
	
	public Page<ExternalReference> getPage(Pageable pageable){
		Page<NeoExternalReference> pageNeoExternalReference = neoExternalReferenceRepository.findAll(pageable,2);
		Page<ExternalReference> pageExternalReference = pageNeoExternalReference.map(neoExternalReferenceToExternalReferenceConverter);		
		return pageExternalReference;
	}
	
	public ExternalReference getExternalReference(String urlHash) {
		NeoExternalReference neoExternalReference = neoExternalReferenceRepository.findOneByUrlHash(urlHash,2);
		if (neoExternalReference == null) {
			return null;
		} else {
			return neoExternalReferenceToExternalReferenceConverter.convert(neoExternalReference);
		}
	}
	
	public Page<ExternalReferenceLink> getExternalReferenceLinksForSample(String accession, Pageable pageable) {
		Page<NeoExternalReferenceLink> pageNeoExternalReferenceLink = neoExternalReferenceLinkRepository.findBySampleAccession(accession, pageable);		
		//get them in greater depth
		pageNeoExternalReferenceLink = pageNeoExternalReferenceLink.map(nxr -> neoExternalReferenceLinkRepository.findOneByHash(nxr.getHash(), 2));		
		//convert them into a state to return
		Page<ExternalReferenceLink> pageExternalReference = pageNeoExternalReferenceLink.map(neoExternalReferenceLinkToExternalReferenceLinkConverter);		
		return pageExternalReference;
	}

	public ExternalReferenceLink getExternalReferenceLink(String hash) {
		NeoExternalReferenceLink neo = neoExternalReferenceLinkRepository.findOneByHash(hash, 1);
		ExternalReferenceLink link = neoExternalReferenceLinkToExternalReferenceLinkConverter.convert(neo);
		return link;
	}

}