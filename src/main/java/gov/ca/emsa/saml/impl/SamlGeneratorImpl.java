package gov.ca.emsa.saml.impl;

import org.opensaml.common.SAMLObjectBuilder;
import org.opensaml.xml.XMLObjectBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import gov.ca.emsa.saml.SAMLInput;
import gov.ca.emsa.saml.SamlGenerator;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;

import javax.security.auth.x500.X500Principal;

import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.signature.XMLSignature;
import org.joda.time.DateTime;
import org.opensaml.Configuration;
import org.opensaml.DefaultBootstrap;
import org.opensaml.common.SAMLVersion;
import org.opensaml.saml2.core.impl.*;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.saml2.core.AttributeValue;
import org.opensaml.saml2.core.AuthnContext;
import org.opensaml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml2.core.AuthnStatement;
import org.opensaml.saml2.core.Conditions;
import org.opensaml.saml2.core.Issuer;
import org.opensaml.saml2.core.NameID;
import org.opensaml.saml2.core.OneTimeUse;
import org.opensaml.saml2.core.Subject;
import org.opensaml.saml2.core.SubjectConfirmation;
import org.opensaml.saml2.core.SubjectConfirmationData;
import org.opensaml.saml2.core.impl.AuthnContextImpl;
import org.opensaml.saml2.core.impl.AuthnContextClassRefImpl;
import org.opensaml.saml2.core.impl.AuthnStatementImpl;
import org.opensaml.saml2.core.Condition;
import org.opensaml.saml2.core.impl.ConditionsImpl;
import org.opensaml.saml2.core.impl.IssuerImpl;
import org.opensaml.saml2.core.impl.NameIDImpl;
import org.opensaml.saml2.core.impl.OneTimeUseImpl;
import org.opensaml.saml2.core.impl.SubjectImpl;
import org.opensaml.saml2.core.impl.SubjectConfirmationImpl;
import org.opensaml.saml2.core.impl.SubjectConfirmationDataImpl;
import org.opensaml.saml2.core.impl.AssertionBuilder;
import org.opensaml.saml2.core.impl.AssertionMarshaller;
import org.opensaml.saml2.core.impl.AttributeStatementBuilder;
import org.opensaml.saml2.core.impl.ConditionsBuilder;
import org.opensaml.saml2.core.impl.IssuerBuilder;
import org.opensaml.saml2.core.impl.NameIDBuilder;
import org.opensaml.saml2.core.impl.SubjectBuilder;
import org.opensaml.saml2.core.impl.SubjectConfirmationBuilder;
import org.opensaml.saml2.core.impl.SubjectConfirmationDataBuilder;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.XMLObjectBuilderFactory;
import org.opensaml.xml.io.MarshallingException;
import org.opensaml.xml.schema.XSString;
import org.opensaml.xml.security.SecurityHelper;
import org.opensaml.xml.security.credential.Credential;
import org.opensaml.xml.security.keyinfo.KeyInfoGenerator;
import org.opensaml.xml.security.keyinfo.KeyInfoGeneratorFactory;
import org.opensaml.xml.security.keyinfo.KeyInfoGeneratorManager;
import org.opensaml.xml.security.x509.BasicX509Credential;
import org.opensaml.xml.security.x509.X509KeyInfoGeneratorFactory;
import org.opensaml.xml.signature.KeyInfo;
import org.opensaml.xml.signature.Signature;
import org.opensaml.xml.signature.impl.SignatureBuilder;
import org.opensaml.xml.util.XMLHelper;

@Service
public class SamlGeneratorImpl implements SamlGenerator {
	
	@Value("${privateKey}")
	private String privateKeyLocation;
	
	@Value("${publicKey}")
	private String publicKeyLocation;

	private XMLObjectBuilderFactory builderFactory;

	public XMLObjectBuilderFactory getSAMLBuilder() throws ConfigurationException{

		if(builderFactory == null){
			DefaultBootstrap.bootstrap();
			builderFactory = Configuration.getBuilderFactory();
		}

		return builderFactory;
	}
	
	public AssertionImpl createSAML(SAMLInput input) throws MarshallingException{
		
		AssertionImpl assertion = buildDefaultAssertion(input);
		AssertionMarshaller marshaller = new AssertionMarshaller();
		org.w3c.dom.Element plaintextElement = marshaller.marshall(assertion);
		String originalAssertionString = XMLHelper.nodeToString(plaintextElement);
		
		return assertion;
	}

	/**
	 * Builds a SAML Attribute of type String
	 * @param name
	 * @param value
	 * @param builderFactory
	 * @return
	 * @throws ConfigurationException
	 */
	public AttributeImpl buildStringAttribute(String name, String value, XMLObjectBuilderFactory builderFactory) throws ConfigurationException
	{
		SAMLObjectBuilder<AttributeImpl> attrBuilder = (SAMLObjectBuilder<AttributeImpl>) getSAMLBuilder().getBuilder(Attribute.DEFAULT_ELEMENT_NAME);
		AttributeImpl attrFirstName = (AttributeImpl) attrBuilder.buildObject();
		attrFirstName.setName(name);

		// Set custom Attributes
		XMLObjectBuilder stringBuilder = getSAMLBuilder().getBuilder(XSString.TYPE_NAME);
		XSString attrValueFirstName = (XSString) stringBuilder.buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSString.TYPE_NAME);
		attrValueFirstName.setValue(value);

		attrFirstName.getAttributeValues().add(attrValueFirstName);
		return attrFirstName;
	}

	/**
	 * Helper method which includes some basic SAML fields which are part of almost every SAML Assertion.
	 *
	 * @param input
	 * @return
	 */
	public AssertionImpl buildDefaultAssertion(SAMLInput input)
	{
		try
		{
			// required SAML assertion fields are Issuer, Subject, AuthnStatement, AttributeStatement
			
			// Create the NameIdentifier
			SAMLObjectBuilder<NameIDImpl> nameIdBuilder = (SAMLObjectBuilder<NameIDImpl>) getSAMLBuilder().getBuilder(NameID.DEFAULT_ELEMENT_NAME);
			NameIDImpl nameId = (NameIDImpl) nameIdBuilder.buildObject();
			nameId.setValue(input.getStrNameID());
			nameId.setFormat(NameID.X509_SUBJECT);

			// Create the SubjectConfirmation

			SAMLObjectBuilder<SubjectConfirmationImpl> confirmationMethodBuilder = (SAMLObjectBuilder<SubjectConfirmationImpl>)  getSAMLBuilder().getBuilder(SubjectConfirmationData.DEFAULT_ELEMENT_NAME);
			SubjectConfirmationData confirmationMethod = (SubjectConfirmationData) confirmationMethodBuilder.buildObject();
			DateTime now = new DateTime();
			confirmationMethod.setNotBefore(now);
			confirmationMethod.setNotOnOrAfter(now.plusMinutes(2));

			SAMLObjectBuilder<SubjectConfirmationImpl> subjectConfirmationBuilder = (SAMLObjectBuilder<SubjectConfirmationImpl>) getSAMLBuilder().getBuilder(SubjectConfirmation.DEFAULT_ELEMENT_NAME);
			SubjectConfirmationImpl subjectConfirmation = (SubjectConfirmationImpl) subjectConfirmationBuilder.buildObject();
			subjectConfirmation.setSubjectConfirmationData(confirmationMethod);

			// Create the Subject
			SAMLObjectBuilder<SubjectImpl> subjectBuilder = (SAMLObjectBuilder<SubjectImpl>) getSAMLBuilder().getBuilder(Subject.DEFAULT_ELEMENT_NAME);
			SubjectImpl subject = (SubjectImpl) subjectBuilder.buildObject();
			subject.setNameID(nameId);
			subject.getSubjectConfirmations().add(subjectConfirmation);

			// Create Authentication Statement
			SAMLObjectBuilder<AuthnStatementImpl> authStatementBuilder = (SAMLObjectBuilder<AuthnStatementImpl>) getSAMLBuilder().getBuilder(AuthnStatement.DEFAULT_ELEMENT_NAME);
			AuthnStatementImpl authnStatement = (AuthnStatementImpl) authStatementBuilder.buildObject();
			DateTime now2 = new DateTime();
			authnStatement.setAuthnInstant(now2);

			SAMLObjectBuilder<AuthnContextImpl> authContextBuilder = (SAMLObjectBuilder<AuthnContextImpl>) getSAMLBuilder().getBuilder(AuthnContext.DEFAULT_ELEMENT_NAME);
			AuthnContextImpl authnContext = (AuthnContextImpl) authContextBuilder.buildObject();

			SAMLObjectBuilder<AuthnContextClassRefImpl> authContextClassRefBuilder = (SAMLObjectBuilder<AuthnContextClassRefImpl>) getSAMLBuilder().getBuilder(AuthnContextClassRef.DEFAULT_ELEMENT_NAME);
			AuthnContextClassRefImpl authnContextClassRef = (AuthnContextClassRefImpl) authContextClassRefBuilder.buildObject();
			authnContextClassRef.setAuthnContextClassRef("urn:oasis:names:tc:SAML:2.0:ac:classes:Password"); // TODO not sure exactly about this

			authnContext.setAuthnContextClassRef(authnContextClassRef);
			authnStatement.setAuthnContext(authnContext);

			// Builder Attributes
			SAMLObjectBuilder<AttributeStatementImpl> attrStatementBuilder = (SAMLObjectBuilder) getSAMLBuilder().getBuilder(AttributeStatement.DEFAULT_ELEMENT_NAME);
			AttributeStatementImpl attrStatement = (AttributeStatementImpl) attrStatementBuilder.buildObject();

			Map<String, String> attributes = input.getAttributes();
			if(attributes != null){
				Iterator<String> keySet = attributes.keySet().iterator();
				while (keySet.hasNext())
				{
					String key = keySet.next().toString();
					String val = "";
					if(attributes.get(key) != null) {
						val = attributes.get(key).toString();
					}
					AttributeImpl attrFirstName = buildStringAttribute(key, val, getSAMLBuilder());
					attrStatement.getAttributes().add(attrFirstName);
				}
			}

			// Create the do-not-cache condition
			SAMLObjectBuilder<Condition> doNotCacheConditionBuilder = (SAMLObjectBuilder<Condition>) getSAMLBuilder().getBuilder(OneTimeUse.DEFAULT_ELEMENT_NAME);
			Condition condition = (Condition) doNotCacheConditionBuilder.buildObject();

			SAMLObjectBuilder<ConditionsImpl> conditionsBuilder = (SAMLObjectBuilder<ConditionsImpl>) getSAMLBuilder().getBuilder(Conditions.DEFAULT_ELEMENT_NAME);
			ConditionsImpl conditions = (ConditionsImpl) conditionsBuilder.buildObject();
			conditions.getConditions().add(condition);

			// Create Issuer
			SAMLObjectBuilder<IssuerImpl> issuerBuilder = (SAMLObjectBuilder<IssuerImpl>) getSAMLBuilder().getBuilder(Issuer.DEFAULT_ELEMENT_NAME);
			IssuerImpl issuer = (IssuerImpl) issuerBuilder.buildObject();
			issuer.setFormat(Issuer.X509_SUBJECT);
			issuer.setValue(input.getStrIssuer());

			// Create the assertion
			SAMLObjectBuilder<AssertionImpl> assertionBuilder = (SAMLObjectBuilder<AssertionImpl>) getSAMLBuilder().getBuilder(Assertion.DEFAULT_ELEMENT_NAME);
			AssertionImpl assertion = (AssertionImpl) assertionBuilder.buildObject();
			// Required #1
			assertion.setVersion(SAMLVersion.VERSION_20);
			// Required #2
			assertion.setID(input.getAssertionId());
			// Required #3
			assertion.setIssueInstant(now);
			// Required #4
			assertion.setIssuer(issuer);
			// Required #5
			assertion.setSubject(subject);
			
			assertion.getAuthnStatements().add(authnStatement);
			assertion.getAttributeStatements().add(attrStatement);
			assertion.setConditions(conditions);
			
			return assertion;
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return null;
	}

}