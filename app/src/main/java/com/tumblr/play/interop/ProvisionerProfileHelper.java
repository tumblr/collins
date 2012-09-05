package com.tumblr.play.interop;

import java.io.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

public class ProvisionerProfileHelper {
  public static JProvisionerProfile fromFile(final File file) throws FileNotFoundException {
    if (file == null) {
      throw new FileNotFoundException("null obviously could not be found");
    }
    final Representer r = new Representer();
    r.getPropertyUtils().setSkipMissingProperties(true);
    final Constructor c = new Constructor(JProvisionerProfile.class);
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
