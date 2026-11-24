const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function sampleProject(uid = ALICE_UID) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    name: "Launch Sprint",
    description: "Ship Material 3 Expressive release",
    colorKey: "CORAL",
    iconKey: "ROCKET",
    deadlineMillis: Date.now() + 86400000,
    archived: false,
    createdAt: now,
    updatedAt: now,
  };
}

function sampleWorkItem(uid = ALICE_UID, projectId = "proj_1") {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    projectId,
    title: "Design live countdown notification",
    notes: "Use Android 16 ProgressStyle Pomodoro segments",
    priority: "HIGH",
    status: "TODO",
    scheduledStartMillis: Date.now() + 3600000,
    projectedDurationMinutes: 50,
    deadlineMillis: Date.now() + 14400000,
    progressPercent: 0,
    elapsedSeconds: 0,
    sessionStartedAtMillis: 0,
    pomodoroWorkMinutes: 25,
    pomodoroBreakMinutes: 5,
    pomodoroWorkPeriods: 2,
    pomodoroBreakCount: 1,
    pomodoroPhase: "WORK",
    currentPeriodIndex: 1,
    phaseElapsedSeconds: 0,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated users are denied reads and writes on projects and workItems", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("workItems").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1").set(sampleProject())
  );
});

test("Cross-user access is denied even for authenticated users", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(BOB_UID).collection("projects").doc("bob_proj").set(sampleProject(BOB_UID));
    await db.collection("users").doc(BOB_UID).collection("workItems").doc("bob_task").set(sampleWorkItem(BOB_UID, "bob_proj"));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("projects").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("workItems").doc("bob_task").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("projects").doc("bob_proj").update({
      name: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can create, list, update, and delete projects with valid Kotlin payloads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1");

  await assertSucceeds(projRef.set(sampleProject()));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("projects").get());

  await assertSucceeds(
    projRef.update({
      name: "Launch Sprint v2",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_bad").set({
      ...sampleProject(),
      id: "proj_bad",
    })
  );
  await assertFails(
    projRef.update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  await assertSucceeds(projRef.delete());
});

test("WorkItem creation, Pomodoro phase transitions, legacy v1 docs, and terminal DONE locks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1");
  const taskRef = aliceDb.collection("users").doc(ALICE_UID).collection("workItems").doc("task_1");
  const legacyRef = aliceDb.collection("users").doc(ALICE_UID).collection("workItems").doc("task_legacy");

  await assertFails(taskRef.set(sampleWorkItem(ALICE_UID, "missing_proj")));

  await assertSucceeds(projRef.set(sampleProject()));
  await assertSucceeds(taskRef.set(sampleWorkItem(ALICE_UID, "proj_1")));

  // Start Work 1 -> transition to Break 1 -> Work 2 -> Complete
  await assertSucceeds(
    taskRef.update({
      status: "IN_PROGRESS",
      pomodoroPhase: "WORK",
      currentPeriodIndex: 1,
      phaseElapsedSeconds: 0,
      sessionStartedAtMillis: Date.now(),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    taskRef.update({
      pomodoroPhase: "BREAK",
      currentPeriodIndex: 1,
      phaseElapsedSeconds: 0,
      elapsedSeconds: 1500,
      progressPercent: 50,
      sessionStartedAtMillis: Date.now(),
      updatedAt: new Date(Date.now() - 400),
    })
  );

  await assertSucceeds(
    taskRef.update({
      status: "DONE",
      progressPercent: 100,
      elapsedSeconds: 3000,
      phaseElapsedSeconds: 0,
      sessionStartedAtMillis: 0,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Editing details while DONE must fail
  await assertFails(
    taskRef.update({
      title: "Mutate after done",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reopening DONE -> TODO succeeds
  await assertSucceeds(
    taskRef.update({
      status: "TODO",
      progressPercent: 80,
      pomodoroPhase: "WORK",
      phaseElapsedSeconds: 0,
      sessionStartedAtMillis: 0,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject invalid Pomodoro phase
  await assertFails(
    taskRef.update({
      pomodoroPhase: "NAP",
      updatedAt: new Date(Date.now() - 50),
    })
  );

  // Legacy v1 WorkItem doc (missing Pomodoro fields) can still be updated cleanly
  const now = new Date(Date.now() - 3000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).collection("workItems").doc("task_legacy").set({
      userId: ALICE_UID,
      projectId: "proj_1",
      title: "Existing task",
      notes: "",
      priority: "MEDIUM",
      status: "TODO",
      scheduledStartMillis: Date.now(),
      projectedDurationMinutes: 45,
      deadlineMillis: 0,
      progressPercent: 0,
      elapsedSeconds: 0,
      sessionStartedAtMillis: 0,
      createdAt: now,
      updatedAt: now,
    });
  });

  await assertSucceeds(
    legacyRef.update({
      status: "IN_PROGRESS",
      pomodoroPhase: "WORK",
      currentPeriodIndex: 1,
      phaseElapsedSeconds: 0,
      sessionStartedAtMillis: Date.now(),
      updatedAt: new Date(Date.now() - 100),
    })
  );
});
