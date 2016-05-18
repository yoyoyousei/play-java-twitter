package controllers;

import models.*;
import org.apache.commons.compress.utils.IOUtils;
import play.data.Form;
import play.data.FormFactory;
import play.filters.csrf.AddCSRFToken;
import play.filters.csrf.RequireCSRFCheck;
import play.mvc.*;
import views.html.index;
import views.html.login;
import views.html.mypage;
import views.html.register;

import javax.inject.Inject;
import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static util.GeneralUtil.sha512;

public class HomeController extends Controller {
    @Inject
    WebJarAssets webJarAssets;
    @Inject
    FormFactory formfactory;

    @Security.Authenticated(models.Secured.class)
    public Result index() {
        if (session("user_id") == null)
            return redirect(routes.HomeController.loginPage());

        User u = User.find.where()
                .eq("user_id", session("user_id"))
                .findUnique();

        if (u == null)
            return redirect(routes.HomeController.loginPage());

        List<Tweet> list =
                u.tweets == null ? new ArrayList<>() : u.tweets;
        Collections.reverse(list);

        String userName = session("user_name");
        if (userName == null)
            userName = "NONAME";


        String content = session("biography");
        if (content == null)
            content = "let's write an introduction of yourself!";

        u.user_name = userName;
        u.biography = content;

        return ok(index.render(list, formfactory.form(Tweet.class), u));
    }

    //セッションにCSRFトークンを格納
    @AddCSRFToken
    public Result loginPage() {
        Form<Login> f = formfactory.form(Login.class);
        return ok(login.render(f));
    }

    //セッションに正しいCSRFトークンが格納されていないとリクエストを受け付けない(?)
    @RequireCSRFCheck
    public Result authenticate() { //dynamicform.get()...return null if the key does not exist
        Form<Login> f = formfactory.form(Login.class).bindFromRequest();  //dynamicformはモデルに関係しないフォームデータを扱う場合に使用する
        if (f.hasErrors()) {
            return badRequest(login.render(f));
        } else {
            //playはデフォルトでセッションにusernameが格納されているかでログイン状態を判断．
            //これに加えてセッションにCSRFTokenが正しく格納されているかでログイン状態を安全に判定できる
            String userId = f.get().getUserId();
            session("username", userId);

            //マイページなど用にユーザデータをセッションに格納
            try {
                User u = User.find.where()
                        .eq("user_id", userId).findUnique();
                //以下のnullチェックについて．session(key)がkeyが無いときにnullを返すことから必要
                //http://webcache.googleusercontent.com/search?
                // q=cache:fSj3W9xRfswJ:microscopium.eyesaac.com/2015/11/27/play2redirect/+&cd=3
                // &hl=ja&ct=clnk&gl=jp
                if (u.user_id != null)
                    session("user_id", u.user_id);

                if (u.user_name != null)
                    session("user_name", u.user_name);

                if (u.biography != null)
                    session("biography", u.biography);

            } catch (Exception e) {
                e.printStackTrace();
                session().clear();
                return redirect(routes.HomeController.index());
            }
            String returnUrl = ctx().session().get("returnUrl");
            if (returnUrl == null || Objects.equals(returnUrl, "")
                    || Objects.equals(returnUrl, routes.HomeController.loginPage().absoluteURL(request()))) {
                returnUrl = routes.HomeController.index().url();
            }
            return redirect(returnUrl);
        }
    }

    @RequireCSRFCheck
    public Result tweet() {
        Form<Tweet> f = formfactory.form(Tweet.class).bindFromRequest();
        if (!f.hasErrors()) {
            Tweet t = new Tweet(f.get().content);
            try {
                t.user = User.find.where().eq("user_id", session("user_id")).findUnique();
                t.save();
            } catch (Exception e) {
                e.printStackTrace();
                //return redirect(routes.HomeController.tweetPage());
                return redirect(routes.HomeController.index());
            }
            return redirect(routes.HomeController.index());
        } else {
            return redirect(routes.HomeController.index());
        }
    }

    @Security.Authenticated(models.Secured.class)
    public Result myPage() {
        User u = User.find.where()
                .eq("user_id", session("user_id"))
                .findUnique();
        if(u==null)
            return redirect(routes.HomeController.loginPage());

        Form<UserProperty> f = formfactory.form(UserProperty.class)
                .fill(new UserProperty(u.user_name, u.biography));
        return ok(mypage.render(f, u));
    }

    @RequireCSRFCheck
    public Result changeProperty() {
        Form<UserProperty> f = formfactory.form(UserProperty.class).bindFromRequest();
        User u = User.find.where()
                .eq("user_id", session("user_id"))
                .findUnique();
        if (!f.hasErrors()) {
            UserProperty up = f.get();
            if (up.userName != null) {
                u.user_name = up.userName;
                session("user_name", u.user_name);
            }

            if (up.biography != null) {
                u.biography = up.biography;
                session("biography", u.biography);
            }

            try {
                u.save();
            } catch (Exception e) {
                e.printStackTrace();
                return redirect(routes.HomeController.changeProperty());
            }
            return redirect(routes.HomeController.index());
        } else {
            return Results.badRequest(mypage.render(f, u));
        }
    }

    @Security.Authenticated(models.Secured.class)
    public Result getUserIcon() {
        String uid = session("user_id");

        if (uid == null)
            return Results.redirect(routes.HomeController.loginPage());

        User u = User.find.where()
                .eq("user_id", uid)
                .findUnique();
        if (u == null)
            return Results.redirect(routes.HomeController.loginPage());

        if (u.user_icon == null || u.user_icon.data == null) {
            try (InputStream iStream = new BufferedInputStream(
                    new FileInputStream("public/images/noimage-twitter.png"))) {
                byte[] noimage = IOUtils.toByteArray(iStream);
                return ok(noimage).as("image");
            } catch (IOException e) {
                e.printStackTrace();
                return internalServerError();
            }

        } else
            return ok(u.user_icon.data).as("image");
    }

    @Security.Authenticated(models.Secured.class)
    public Result uploadIcon() {
        Http.MultipartFormData.FilePart picture=request()
                .body()
                .asMultipartFormData()
                .getFile("picture");
        if(picture!=null) {
            File file=(File)picture.getFile();

            String uid = session("user_id");
            if (uid == null)
                return Results.redirect(routes.HomeController.loginPage());
            User u = User.find.where()
                    .eq("user_id", uid)
                    .findUnique();
            if (u == null)
                return Results.redirect(routes.HomeController.loginPage());

            UserIcon ui=new UserIcon(file);
            u.user_icon=ui;
            u.update();
        }
        return badRequest();
    }


    public Result registerPage() {
        Form<User> f = formfactory.form(User.class);
        return ok(register.render(f));
    }

    public Result register() throws NoSuchAlgorithmException {
        Form<User> f = formfactory.form(User.class).bindFromRequest();
        if (!f.hasErrors()) {
            User u = new User(f.get().user_id,
                    sha512(f.get().password));
            u.user_name = "NONAME";
            u.biography = "let's write an introduction of yourself!";
            try {
                u.save();
            } catch (Exception e) {
                e.printStackTrace();
                return Results.badRequest(register.render(f));
            }
            return redirect(routes.HomeController.loginPage());
        } else {
            return Results.badRequest(register.render(f));
        }
    }

    @Security.Authenticated(Secured.class)
    public Result logout() {
        session().clear();
        return redirect(routes.HomeController.loginPage());
    }

    public boolean isPicture(File file) {
        String ext = file.getName().substring(file.getName().lastIndexOf(".") + 1);
        if (file.isFile() && (
                ext.equals("png") || ext.equals("jpeg") || ext.equals("jpg")))
            return true;
        return false;
    }


}
