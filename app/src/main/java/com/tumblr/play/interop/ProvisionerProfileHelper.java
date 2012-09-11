package com.tumblr.play.interop;

import java.io.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.constructor.*;

public class ProvisionerProfileHelper {
  public static JProvisionerProfile fromFile(final File file, final ClassLoader classLoader) throws FileNotFoundException {
    if (file == null) {
      throw new FileNotFoundException("null obviously could not be found");
    }
    final Representer r = new Representer();
    r.getPropertyUtils().setSkipMissingProperties(true);
    final Constructor c = new CustomClassLoaderConstructor(JProvisionerProfile.class, classLoader);
    final Yaml yaml = new Yaml(c, r);
    final InputStream fis = new FileInputStream(file);
    JProvisionerProfile p = null;
    try {
      p = (JProvisionerProfile) yaml.load(fis);
    } finally {
      try {
        fis.close();
      } catch (Exception e) { }
    }
    return p;
  }
}
