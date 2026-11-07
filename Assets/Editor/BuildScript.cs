using System;
using System.IO;
using UnityEditor;
using UnityEditor.Build.Reporting;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;

/// <summary>
/// Dipanggil GameCI: buildMethod = BuildScript.BuildAndroid.
/// Argumen: -packageId, -versionCode, -aab true|false,
/// -keystorePath, -keystorePass, -keyAlias, -keyAliasPass.
/// </summary>
public static class BuildScript
{
    private const string ScenePath = "Assets/Scenes/Main.unity";
    private const string DefaultPackageId = "com.udyneos.neontetris";
    private const string Version = "1.1.0";

    public static void BuildAndroid()
    {
        try
        {
            ConfigurePlayer();
            EnsureScene();
            RunBuild();
        }
        catch (Exception e)
        {
            Debug.LogError($"[Build] Gagal: {e}");
            EditorApplication.Exit(1);
        }
    }

    private static void ConfigurePlayer()
    {
        string packageId = GetArg("packageId") ?? DefaultPackageId;
        int versionCode = int.TryParse(GetArg("versionCode"), out int vc) ? vc : 1;

        PlayerSettings.productName = "Neon Tetris";
        PlayerSettings.bundleVersion = Version;
        PlayerSettings.SetApplicationIdentifier(BuildTargetGroup.Android, packageId);
        PlayerSettings.SetScriptingBackend(BuildTargetGroup.Android, ScriptingImplementation.IL2CPP);
        PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARMv7 | AndroidArchitecture.ARM64;
        PlayerSettings.Android.minSdkVersion = AndroidSdkVersions.AndroidApiLevel23; // Android 6.0
        PlayerSettings.Android.targetSdkVersion = AndroidSdkVersions.AndroidApiLevelAuto;
        PlayerSettings.Android.bundleVersionCode = versionCode;
        PlayerSettings.defaultInterfaceOrientation = UIOrientation.Portrait;
        PlayerSettings.SetUseDefaultGraphicsAPIs(BuildTarget.Android, false);
        PlayerSettings.SetGraphicsAPIs(BuildTarget.Android, new[] { GraphicsDeviceType.OpenGLES3 });

        bool aab = GetArg("aab") == "true";
        EditorUserBuildSettings.buildAppBundle = aab;

        string keystorePath = GetArg("keystorePath");
        if (!string.IsNullOrEmpty(keystorePath))
        {
            string full = Path.GetFullPath(Path.Combine(ProjectRoot(), keystorePath));
            if (!File.Exists(full)) throw new FileNotFoundException("Keystore tidak ditemukan", full);

            PlayerSettings.Android.useCustomKeystore = true;
            PlayerSettings.Android.keystoreName = full;
            PlayerSettings.Android.keystorePass = Require("keystorePass");
            PlayerSettings.Android.keyaliasName = Require("keyAlias");
            PlayerSettings.Android.keyaliasPass = Require("keyAliasPass");
        }
        else
        {
            PlayerSettings.Android.useCustomKeystore = false;
        }
    }

    private static void EnsureScene()
    {
        Directory.CreateDirectory(Path.Combine(ProjectRoot(), "Assets", "Scenes"));
        var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);
        if (!EditorSceneManager.SaveScene(scene, ScenePath))
            throw new InvalidOperationException("Gagal menyimpan scene " + ScenePath);

        EditorBuildSettings.scenes = new[] { new EditorBuildSettingsScene(ScenePath, true) };
    }

    private static void RunBuild()
    {
        bool aab = GetArg("aab") == "true";
        string dir = GetArg("customBuildPath") ?? Path.Combine(ProjectRoot(), "build", "Android");
        string name = GetArg("customBuildName") ?? "NeonTetris";
        Directory.CreateDirectory(dir);

        var options = new BuildPlayerOptions
        {
            scenes = new[] { ScenePath },
            locationPathName = Path.Combine(dir, name + (aab ? ".aab" : ".apk")),
            target = BuildTarget.Android,
            targetGroup = BuildTargetGroup.Android,
            options = BuildOptions.None
        };

        BuildReport report = BuildPipeline.BuildPlayer(options);
        BuildSummary summary = report.summary;
        Debug.Log($"[Build] {summary.result} -> {summary.outputPath} ({summary.totalSize} bytes)");

        if (summary.result != BuildResult.Succeeded)
            EditorApplication.Exit(2);
    }

    private static string ProjectRoot() => Directory.GetParent(Application.dataPath).FullName;

    private static string Require(string name) =>
        GetArg(name) ?? throw new ArgumentException($"Argumen -{name} wajib diisi untuk build bertanda tangan");

    private static string GetArg(string name)
    {
        string[] args = Environment.GetCommandLineArgs();
        for (int i = 0; i < args.Length - 1; i++)
            if (args[i] == "-" + name) return args[i + 1];
        return null;
    }
}
