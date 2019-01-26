package cz.zcu.kiv.offscreen.servlets.api;

import com.google.common.base.Strings;
import com.google.gson.JsonObject;
import cz.zcu.kiv.offscreen.modularization.ModuleProvider;
import cz.zcu.kiv.offscreen.servlets.BaseServlet;
import javafx.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.shared.utils.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * This class is used for loading diagrams from session.
 */
public class GetSessionDiagram extends BaseServlet {
    private static final Logger logger = LogManager.getLogger();

    /**
     * Constructs graph data using either the current graph version or the version set in query parameter. Resulting
     * graph is returned as JSON in response body.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        logger.debug("Processing request");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        getDiagramFromSession(request, response);
    }

    /**
     * Add file which was uploaded and is stored in session to response or set http status code to BAD_REQUEST.
     */
    private void getDiagramFromSession(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String diagramToDisplay = (String) request.getSession().getAttribute("diagram_string");
        String diagramType = (String) request.getSession().getAttribute("diagram_type");
        String filename = (String) request.getSession().getAttribute("diagram_filename");

        if (!Strings.isNullOrEmpty(diagramToDisplay) && diagramType != null) {

            String rawJson;

            if (diagramType.equals("raw")) {
                logger.debug("Processing Raw json");
                rawJson = diagramToDisplay;
            } else {
                Optional<String> optional = callModuleConverter(diagramType, diagramToDisplay);
                if(optional.isPresent()){
                    rawJson = optional.get();
                } else {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
            }

            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("graph_json", rawJson);
            jsonObject.addProperty("name", filename);

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(jsonObject.toString());
            response.getWriter().flush();
            logger.debug("Response OK");
            return;
        }

        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        logger.debug("Response BAD REQUEST");
    }

    /**
     * Method find module by type and call method for getting RAW JSON from module. If result is null, empty or
     * blank, method returns empty Optional.
     *
     * @param type type of converter which is key to map of modules
     * @param stringToConvert string which will be converted
     * @return Optional of RAW JSON or empty Optional
     */
    private Optional<String> callModuleConverter(String type, String stringToConvert){
        logger.debug("Processing json with module");

        Pair<String, Class> module = ModuleProvider.getInstance().getModules().get(type);
        if (module == null){
            logger.debug("No loader available for type: " + type + ". Response BAD REQUEST");
            return Optional.empty();
        }

        try {
            final Class<?> moduleClass = module.getValue();
            // switching to class loader of module
            final ClassLoader appClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(moduleClass.getClassLoader());

            final Method moduleMethod = moduleClass.getMethod(ModuleProvider.METHOD_NAME, ModuleProvider.METHOD_PARAMETER_CLASS);
            String rawJson = String.valueOf(moduleMethod.invoke(moduleClass.newInstance(), stringToConvert));

            // switching back to application class loader
            Thread.currentThread().setContextClassLoader(appClassLoader);

            if(StringUtils.isBlank(rawJson)){
                return Optional.empty();
            } else {
                return Optional.of(rawJson);
            }

        } catch (Exception e) {
            logger.error("Can not call convert method in module. Module name: " + module.getKey(), e);
            return Optional.empty();
        }
    }
}
