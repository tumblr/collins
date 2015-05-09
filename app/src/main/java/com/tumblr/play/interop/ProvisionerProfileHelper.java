package com.tumblr.play.interop;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

public class ProvisionerProfileHelper {
  public static JProvisionerProfile fromFile(final File file) throws FileNotFoundException {
    if (file == null) {
      throw new FileNotFoundException("null obviously could not be found");
    }
    final Representer r = new Representer();
    r.getPropertyUtils().setSkipMissingProperties(true);
    final Yaml yaml = new Yaml(new CustomClassLoaderConstructor(JProvisionerProfile.class), r);
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
