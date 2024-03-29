package inhatc.insecure.veganday.ocr.controller;

import inhatc.insecure.veganday.common.model.FileDTO;
import inhatc.insecure.veganday.common.model.ResponseFmt;
import inhatc.insecure.veganday.common.model.ResponseMessage;
import inhatc.insecure.veganday.common.model.StatusCode;
import inhatc.insecure.veganday.common.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

@RestController
@RequestMapping("/ocr")
@Slf4j
public class OcrController {
    
    @Autowired
    FileService fileService;

    @Value("${imageServer.path}")
    private String imageServerPath;
    
    @Value("${clova.secret}")
    private String secretKey;
    
    @Value("${clova.url}")
    private String apiURL;

	@PostMapping("")
    public ResponseEntity ocr(@RequestBody(required = false) MultipartFile file){

        if(file == null){
            return new ResponseEntity(ResponseFmt.res(StatusCode.BAD_REQUEST, ResponseMessage.DONT_SEND_PARAM), HttpStatus.OK);
        }

        FileDTO filedto = fileService.uploadFile(file);

        if(filedto.getCode() == -2){
            return new ResponseEntity(ResponseFmt.res(StatusCode.BAD_REQUEST, ResponseMessage.IMAGE_UPLOAD_ERROR), HttpStatus.OK);
        }
        if(filedto.getCode() == -1){
            return new ResponseEntity(ResponseFmt.res(StatusCode.BAD_REQUEST, ResponseMessage.CANT_NOT_OTHER_FILES), HttpStatus.OK);
        }

        String imageUrl = imageServerPath + filedto.getFilepath();
        String ocrResult = imageUrl;
        // OCR 수행
        try {
			URL url = new URL(apiURL);
			HttpURLConnection con = (HttpURLConnection)url.openConnection();
			con.setUseCaches(false);
			con.setDoInput(true);
			con.setDoOutput(true);
			con.setRequestMethod("POST");
			con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
			con.setRequestProperty("X-OCR-SECRET", secretKey);

			JSONObject json = new JSONObject();
			json.put("version", "V2");
			json.put("requestId", UUID.randomUUID().toString());
			json.put("timestamp", System.currentTimeMillis());
			JSONObject image = new JSONObject();
			image.put("format", "jpg");
			image.put("url", imageUrl);
			image.put("name", "vegan");
			JSONArray images = new JSONArray();
			images.put(image);
			json.put("images", images);
			String postParams = json.toString();

			DataOutputStream wr = new DataOutputStream(con.getOutputStream());
			wr.writeBytes(postParams);
			wr.flush();
			wr.close();

			int responseCode = con.getResponseCode();
			BufferedReader br;
			if (responseCode == 200) {
				br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
			} else {
				br = new BufferedReader(new InputStreamReader(con.getErrorStream(), StandardCharsets.UTF_8));
			}
			String inputLine;
			StringBuffer response = new StringBuffer();
			while ((inputLine = br.readLine()) != null) {
				response.append(inputLine);
			}
			br.close();

			ocrResult = response.toString();
		} catch (ProtocolException e) {
			log.error(String.valueOf(e));
			return new ResponseEntity(ResponseFmt.res(StatusCode.INTERNAL_SERVER_ERROR, ResponseMessage.OCR_ERROR, ocrResult), HttpStatus.OK);
		} catch (MalformedURLException e) {
			log.error(String.valueOf(e));
			return new ResponseEntity(ResponseFmt.res(StatusCode.INTERNAL_SERVER_ERROR, ResponseMessage.OCR_ERROR, ocrResult), HttpStatus.OK);
		} catch (IOException e) {
			log.error(String.valueOf(e));
			return new ResponseEntity(ResponseFmt.res(StatusCode.INTERNAL_SERVER_ERROR, ResponseMessage.OCR_ERROR, ocrResult), HttpStatus.OK);
		}


		return new ResponseEntity(ResponseFmt.res(StatusCode.OK, ResponseMessage.SAVE_OCR_IMAGE, ocrResult), HttpStatus.OK);
    }
}
    
