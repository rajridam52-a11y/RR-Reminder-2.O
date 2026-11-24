const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_ridam";
const OWNER_EMAIL = "rajridam92@gmail.com";
const USER_UID = "user_alice";
const USER_EMAIL = "alice@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validConfigPayload(overrides = {}) {
  const past = new Date(Date.now() - 2000);
  return {
    timeMode: "GOOGLE_AUTO",
    timeOffsetMinutes: 0,
    fixedTargetEpochMillis: 0,
    fixedAnchorEpochMillis: 0,
    syncOverrideEnabled: true,
    primaryClockZone: "india_ist",
    timeFormatMode: "12h",
    dateFormatStyle: "medium",
    themeMode: "dark",
    accentPreset: "aurora",
    weatherTempUnit: "celsius",
    weatherWindUnit: "kmh",
    bannerEnabled: true,
    bannerMessage: "Owner Live Sync Active",
    updatedByEmail: OWNER_EMAIL,
    createdAt: past,
    updatedAt: past,
    ...overrides,
  };
}

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

test("Unauthenticated user: denied read, list, and write on app_config", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("app_config").doc("global").get());
  await assertFails(unauthDb.collection("app_config").get());
  await assertFails(unauthDb.collection("app_config").doc("global").set(validConfigPayload()));
});

test("Regular signed-in user: can get app_config/global, cannot list or write", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("app_config").doc("global").set(validConfigPayload());
  });

  const userDb = testEnv.authenticatedContext(USER_UID, {
    email: USER_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(userDb.collection("app_config").doc("global").get());
  await assertFails(userDb.collection("app_config").get());
  await assertFails(
    userDb.collection("app_config").doc("global").update({
      timeMode: "OFFSET",
      timeOffsetMinutes: 60,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(userDb.collection("app_config").doc("global").delete());
});

test("Email spoof attack: unverified rajridam92@gmail.com cannot write app_config/global", async () => {
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: OWNER_EMAIL,
    email_verified: false,
  }).firestore();

  await assertFails(spoofDb.collection("app_config").doc("global").set(validConfigPayload()));
});

test("Verified owner rajridam92@gmail.com: valid create and partial update succeed", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    ownerDb.collection("app_config").doc("global").set(validConfigPayload())
  );

  await assertSucceeds(
    ownerDb.collection("app_config").doc("global").update({
      timeMode: "OFFSET",
      timeOffsetMinutes: 90,
      themeMode: "light",
      bannerMessage: "Updated by Ridam Raj",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Verified owner: rejects ghost fields, invalid enums, oversized strings, tampered createdAt, future timestamps, non-global docs, and deletes", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    ownerDb.collection("app_config").doc("global").set(validConfigPayload())
  );

  // Ghost field (@DocumentId 'id')
  await assertFails(
    ownerDb.collection("app_config").doc("global").set(validConfigPayload({ id: "global" }))
  );

  // Non-global doc ID
  await assertFails(
    ownerDb.collection("app_config").doc("other").set(validConfigPayload())
  );

  // Invalid enum
  await assertFails(
    ownerDb.collection("app_config").doc("global").update({
      timeMode: "HACKED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Out of bounds offset
  await assertFails(
    ownerDb.collection("app_config").doc("global").update({
      timeMode: "OFFSET",
      timeOffsetMinutes: 9999,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Oversized banner message (>240)
  await assertFails(
    ownerDb.collection("app_config").doc("global").update({
      bannerMessage: "x".repeat(241),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Immutable createdAt tamper
  await assertFails(
    ownerDb.collection("app_config").doc("global").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Future timestamp
  await assertFails(
    ownerDb.collection("app_config").doc("global").update({
      bannerMessage: "Future",
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Delete forbidden
  await assertFails(ownerDb.collection("app_config").doc("global").delete());
});
