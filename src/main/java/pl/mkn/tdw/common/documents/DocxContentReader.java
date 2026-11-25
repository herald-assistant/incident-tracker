package pl.mkn.tdw.common.documents;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

/** Reads document text and embedded images without executing external relationships. */
public final class DocxContentReader {
    private static final Map<String, String> IMAGES = Map.of(
            "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg", "gif", "image/gif", "webp", "image/webp");
    private DocxContentReader() {}
    public record Image(String name, String mimeType, byte[] content) {}
    public record Content(String text, List<Image> images) {}

    public static Content read(byte[] bytes) {
        var text = new StringBuilder();
        var images = new ArrayList<Image>();
        var foundDocument = false;
        var factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                var name = entry.getName();
                if (name.matches("word/(document|header[0-9]*|footer[0-9]*|footnotes|endnotes|comments)\\.xml")) {
                    text.append('\n').append(name).append('\n');
                    var xml = factory.createXMLStreamReader(new ByteArrayInputStream(zip.readAllBytes()));
                    try {
                        var rootChecked = false;
                        while (xml.hasNext()) {
                            var event = xml.next();
                            if (event == XMLStreamConstants.DTD) throw new IllegalArgumentException("DOCX DTD is not supported");
                            if (event == XMLStreamConstants.START_ELEMENT && !rootChecked) {
                                rootChecked = true;
                                if (name.equals("word/document.xml")) {
                                    if (!xml.getLocalName().equals("document") || !List.of(
                                            "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
                                            "http://purl.oclc.org/ooxml/wordprocessingml/main").contains(xml.getNamespaceURI()))
                                        throw new IllegalArgumentException("Invalid Word document root");
                                    foundDocument = true;
                                }
                            }
                            if (event == XMLStreamConstants.START_ELEMENT && xml.getLocalName().equals("t"))
                                text.append(xml.getElementText());
                            else if (event == XMLStreamConstants.START_ELEMENT && xml.getLocalName().equals("tab")) text.append('\t');
                            else if ((event == XMLStreamConstants.END_ELEMENT && List.of("p", "tc").contains(xml.getLocalName()))
                                    || (event == XMLStreamConstants.START_ELEMENT && List.of("br", "cr").contains(xml.getLocalName())))
                                text.append('\n');
                        }
                    } finally { xml.close(); }
                } else if (name.startsWith("word/media/")) {
                    var extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
                    if (IMAGES.containsKey(extension)) images.add(new Image(name, IMAGES.get(extension), zip.readAllBytes()));
                }
            }
            if (!foundDocument) throw new IllegalArgumentException("Missing word/document.xml");
            return new Content(text.toString(), List.copyOf(images));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Nie można odczytać dokumentu DOCX.", exception);
        }
    }
}
