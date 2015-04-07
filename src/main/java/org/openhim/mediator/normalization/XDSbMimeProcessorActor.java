/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.normalization;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.openhim.mediator.engine.messages.ExceptError;
import org.openhim.mediator.engine.messages.MediatorRequestMessage;
import org.openhim.mediator.engine.messages.SimpleMediatorRequest;
import org.openhim.mediator.engine.messages.SimpleMediatorResponse;

import javax.mail.BodyPart;
import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * A MIME container for processing MTOM/XOP requests.
 * <br/><br/>
 * The actor just parses out the SOAP message, but keeps the request in state
 * so that the complete MTOM request can be sent again with the enriched message.
 * <br/><br/>
 * Messages supported:
 * <ul>
 *     <li>MimeMessage: Parses the string as an MTOM/XOP request and responds with the SOAP part and a map referencing the other documents (XDSbMimeProcessorResponse)</li>
 *     <li>EnrichedMessage: Returns the original MTOM/XOP request but with the provided enriched SOAP part (XDSbMimeProcessorResponse)</li>
 * </ul>
 */
public class XDSbMimeProcessorActor extends UntypedActor {

    public static class MimeMessage extends SimpleMediatorRequest<String> {
        final String contentType;

        public MimeMessage(ActorRef requestHandler, ActorRef respondTo, String requestObject, String contentType) {
            super(requestHandler, respondTo, requestObject);
            this.contentType = contentType;
        }
    }

    public static class EnrichedMessage extends SimpleMediatorRequest<String> {
        public EnrichedMessage(ActorRef requestHandler, ActorRef respondTo, String requestObject) {
            super(requestHandler, respondTo, requestObject);
        }
    }

    public static class XDSbMimeProcessorResponse extends SimpleMediatorResponse<String> {
        final List<String> documents;

        public XDSbMimeProcessorResponse(MediatorRequestMessage originalRequest, String requestObject, List<String> documents) {
            super(originalRequest, requestObject);
            this.documents = documents;
        }

        public List<String> getDocuments() {
            return documents;
        }
    }

    public static class NoPreviousMimeMessage extends Exception {
        public NoPreviousMimeMessage() {
            super("No previous MimeMessage received");
        }
    }

    public static class SOAPPartNotFound extends Exception {
        public SOAPPartNotFound() {
            super("SOAP part wasn't found in mime multipart message");
        }
    }

    public static class UnprocessableContentFound extends Exception {
        public UnprocessableContentFound() {
            super(String.format("Unprocessable content found in SOAP part"));
        }
    }

    MimeMultipart mimeMessage;

    private String _soapPart;
    private List<String> _documents = new ArrayList<>(1);

    private void parseMimeMessage(String msg, String contentType) throws IOException, MessagingException, SOAPPartNotFound, UnprocessableContentFound {
        mimeMessage = new MimeMultipart(new ByteArrayDataSource(msg, contentType));
        for (int i=0; i<mimeMessage.getCount(); i++) {
            BodyPart part = mimeMessage.getBodyPart(i);

            if (part.getContentType().contains("application/soap+xml")) {
                _soapPart = getValue(part);
            } else {
                _documents.add(getValue(part));
            }
        }

        if (_soapPart==null) {
            throw new SOAPPartNotFound();
        }
    }

    private String getValue(BodyPart part) throws IOException, MessagingException, UnprocessableContentFound {
        Object value = part.getContent();
        if (value instanceof String) {
            return (String) value;
        } else if (value instanceof InputStream) {
            return IOUtils.toString((InputStream) value);
        }
        throw new UnprocessableContentFound();
    }

    private String buildEnrichedMimeMessage(String enrichedSOAPMessage) throws MessagingException, IOException {
        for (int i=0; i<mimeMessage.getCount(); i++) {
            BodyPart part = mimeMessage.getBodyPart(i);
            if (part.getContentType().contains("application/soap+xml")) {
                Enumeration copyOfHeaders = createCopyOfHeaders(part);
                part.setContent(enrichedSOAPMessage, part.getContentType());
                copyHeadersToPart(copyOfHeaders, part);
                break;
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        mimeMessage.writeTo(out);
        mimeMessage = null;
        return out.toString();
    }

    private Enumeration createCopyOfHeaders(BodyPart part) throws MessagingException {
        Enumeration headers = part.getAllHeaders();
        InternetHeaders internetHeaders = new InternetHeaders();
        while (headers.hasMoreElements()) {
            Header header = (Header) headers.nextElement();
            internetHeaders.addHeader(header.getName(), header.getValue());
        }
        return internetHeaders.getAllHeaders();
    }

    private void copyHeadersToPart(Enumeration headers, BodyPart part) throws MessagingException {
        while (headers.hasMoreElements()) {
            Header header = (Header) headers.nextElement();
            part.setHeader(header.getName(), header.getValue());
        }
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof MimeMessage) {
            try {
                parseMimeMessage(((MimeMessage) msg).getRequestObject(), ((MimeMessage) msg).contentType);
                ((MimeMessage) msg).getRespondTo().tell(new XDSbMimeProcessorResponse((MediatorRequestMessage) msg, _soapPart, _documents), getSelf());
            } catch (IOException | MessagingException | SOAPPartNotFound | UnprocessableContentFound ex) {
                ((MimeMessage) msg).getRequestHandler().tell(new ExceptError(ex), getSelf());
            }
        } else if (msg instanceof EnrichedMessage) {
            if (mimeMessage==null) {
                ((EnrichedMessage) msg).getRequestHandler().tell(new ExceptError(new NoPreviousMimeMessage()), getSelf());
            } else {
                try {
                    String mime = buildEnrichedMimeMessage(((EnrichedMessage) msg).getRequestObject());
                    ((EnrichedMessage) msg).getRespondTo().tell(new XDSbMimeProcessorResponse((MediatorRequestMessage) msg, mime, _documents), getSelf());
                } catch (MessagingException | IOException ex) {
                    ((EnrichedMessage) msg).getRequestHandler().tell(new ExceptError(ex), getSelf());
                }
            }
        } else {
            unhandled(msg);
        }
    }
}
