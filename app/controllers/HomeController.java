package controllers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.User;
import play.data.DynamicForm;
import play.data.Form;
import play.data.FormFactory;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Result;
import util.ConvertionUtil;
import views.html.index;
import views.html.login;
import views.html.register;

import javax.inject.Inject;
import java.io.File;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class HomeController extends Controller {
    @Inject
    WebJarAssets webJarAssets;
    @Inject
    FormFactory formfactory;

    /**
     * An action that renders an HTML page with a welcome message.
     * The configuration in the <code>routes</code> file means that
     * this method will be called when the application receives a
     * <code>GET</code> request with a path of <code>/</code>.
     */
    public Result index() {
        return ok(index.render());
    }

    public Result loginPage() {
        Form<User> f = formfactory.form(User.class);
        return ok(login.render(f));
    }

    public Result login() {
        return ok();
    }

    public Result registerPage() {
        Form<User> f = formfactory.form(User.class);
        return ok(register.render(f));
    }

    public Result register() {
        DynamicForm f = formfactory.form().bindFromRequest();
        if (!f.hasErrors()) {
            String userId = f.get("userId");
            String password = f.get("password");
            User u = new User(userId, password);
            try {
                u.save();
            } catch (Exception e) {
                e.printStackTrace();
                return redirect("/register");
            }
            return redirect("/login");
        } else {
            return redirect("/register");
        }


    }


    /**
     * ファイルはフォームデータの一部として送られてこない．
     * フォームのデータに添付されて送られてくる．
     * http://stackoverflow.com/questions/9452375/how-to-get-the-upload-file-with-other-inputs-in-play2#9587052
     *
     * @return
     */
    public Result aaConvert() {
        MultipartFormData.FilePart picture = request().body().asMultipartFormData().getFile("picture");
        //Map<String,String[]> form= request().body(). asMultipartFormData().asFormUrlEncoded();//checkboxは値がないときにはmapに要素すら無いので注意
        DynamicForm form = formfactory.form().bindFromRequest();
        if (picture != null) {
            File file = (File) picture.getFile();

            String aa = ConvertionUtil.aaConvertion(file, form);
            ObjectNode result = Json.newObject();
            result.put("aa", aa);
            return ok(result);
        }
        return badRequest("picture is null");
    }

    public boolean isPicture(File file) {
        String ext = file.getName().substring(file.getName().lastIndexOf(".") + 1);
        if (file.isFile() && (
                ext.equals("png") || ext.equals("jpeg") || ext.equals("jpg")))
            return true;
        return false;
    }

}
