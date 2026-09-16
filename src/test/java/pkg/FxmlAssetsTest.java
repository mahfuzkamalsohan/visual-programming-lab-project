package pkg;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;

class FxmlAssetsTest {

    private static final List<String> FXML_FILES = List.of(
            "/assets/ui/fxml/main_menu.fxml",
            "/assets/ui/fxml/pause_menu.fxml",
            "/assets/ui/fxml/question_overlay.fxml",
            "/assets/ui/fxml/game_end_overlay.fxml"
    );

    @Test
    void allFxmlFilesAreValidXmlAndHaveValidImports() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        for (String fxmlPath : FXML_FILES) {
            try (InputStream stream = getClass().getResourceAsStream(fxmlPath)) {
                assertNotNull(stream, "FXML file missing from classpath: " + fxmlPath);
                Document doc = builder.parse(stream);
                assertNotNull(doc);

                // Collect all import statements
                List<String> imports = new ArrayList<>();
                NodeList children = doc.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node node = children.item(i);
                    if (node instanceof ProcessingInstruction pi && "import".equals(pi.getTarget())) {
                        imports.add(pi.getData().trim());
                    }
                }

                // Verify every imported class can be loaded
                for (String imp : imports) {
                    if (imp.endsWith(".*")) {
                        continue; // wildcard import
                    }
                    assertDoesNotThrow(() -> Class.forName(imp, false, getClass().getClassLoader()),
                            "FXML " + fxmlPath + " imports unknown class: " + imp);
                }

                // Verify root element and all child elements correspond to an imported class
                verifyElementClasses(doc.getDocumentElement(), imports, fxmlPath);
            }
        }
    }

    private void verifyElementClasses(Element element, List<String> imports, String fxmlPath) {
        String tagName = element.getTagName();
        // Skip property wrapper elements (e.g. Insets inside padding or margins)
        if (!tagName.contains(".") && Character.isUpperCase(tagName.charAt(0))) {
            boolean matched = imports.stream().anyMatch(imp ->
                    imp.equals(tagName) || imp.endsWith("." + tagName) || imp.endsWith(".*"));
            assertTrue(matched,
                    "Tag <" + tagName + "> in " + fxmlPath + " is not imported via <?import ...?>");
        }

        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child instanceof Element childEl) {
                verifyElementClasses(childEl, imports, fxmlPath);
            }
        }
    }

    @Test
    void allMenuPngAssetsExistAndAreReadable() throws Exception {
        List<String> menuAssets = List.of(
                "/assets/ui/menu/singleplayer.png",
                "/assets/ui/menu/multiplayer.png",
                "/assets/ui/menu/settings.png",
                "/assets/ui/menu/about.png",
                "/assets/ui/menu/exit.png",
                "/assets/ui/menu/host.png",
                "/assets/ui/menu/join.png",
                "/assets/ui/menu/shared.png",
                "/assets/ui/menu/back.png",
                "/assets/ui/menu/resume.png",
                "/assets/ui/menu/main_menu.png",
                "/assets/ui/menu/mainmenu_bg.png",
                "/assets/ui/menu/menu.png",
                "/assets/ui/menu/stick.png"
        );

        for (String assetPath : menuAssets) {
            try (InputStream stream = getClass().getResourceAsStream(assetPath)) {
                assertNotNull(stream, "Menu asset missing: " + assetPath);
                byte[] header = stream.readNBytes(8);
                // Verify standard PNG magic header: 0x89 'P' 'N' 'G' 0x0D 0x0A 0x1A 0x0A
                assertTrue(header.length == 8 && header[0] == (byte) 0x89 && header[1] == 'P'
                                && header[2] == 'N' && header[3] == 'G',
                        "Asset is not a valid PNG: " + assetPath);
            }
        }
    }
}
