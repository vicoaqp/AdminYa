const {
  onCall,
  HttpsError,
} = require("firebase-functions/v2/https");

const {
  initializeApp,
} = require("firebase-admin/app");

const {
  getAuth,
} = require("firebase-admin/auth");

const {
  getFirestore,
  FieldValue,
} = require("firebase-admin/firestore");

initializeApp();

const db = getFirestore();

function obtenerTexto(valor) {
  if (typeof valor !== "string") {
    return "";
  }

  return valor.trim();
}

function esCorreoValido(correo) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo);
}

function soloNumeros(valor) {
  return /^\d+$/.test(valor);
}

exports.crearChofer = onCall(
    {
      region: "us-central1",
    },
    async (request) => {
      /*
       * Verificar que el propietario esté
       * autenticado en AdminYa.
       */
      if (!request.auth) {
        throw new HttpsError(
            "unauthenticated",
            "Debes iniciar sesión para crear un chofer.",
        );
      }

      const propietarioUid = request.auth.uid;
      const datos = request.data || {};

      const nombres =
        obtenerTexto(datos.nombres);

      const apellidos =
        obtenerTexto(datos.apellidos);

      const dni =
        obtenerTexto(datos.dni);

      const celular =
        obtenerTexto(datos.celular);

      const correo =
        obtenerTexto(datos.correo).toLowerCase();

      const password =
        obtenerTexto(datos.password);

      const licencia =
        obtenerTexto(datos.licencia).toUpperCase();

      const categoriaLicencia =
        obtenerTexto(datos.categoriaLicencia).toUpperCase();

      const vencimientoLicencia =
        obtenerTexto(datos.vencimientoLicencia);

      const disponible =
        datos.disponible === true;

      /*
       * Validaciones de los datos.
       */
      if (nombres.length < 2) {
        throw new HttpsError(
            "invalid-argument",
            "Ingresa los nombres del chofer.",
        );
      }

      if (apellidos.length < 2) {
        throw new HttpsError(
            "invalid-argument",
            "Ingresa los apellidos del chofer.",
        );
      }

      if (
        dni.length !== 8 ||
        !soloNumeros(dni)
      ) {
        throw new HttpsError(
            "invalid-argument",
            "El DNI debe tener 8 dígitos.",
        );
      }

      if (
        celular.length !== 9 ||
        !soloNumeros(celular)
      ) {
        throw new HttpsError(
            "invalid-argument",
            "El celular debe tener 9 dígitos.",
        );
      }

      if (!esCorreoValido(correo)) {
        throw new HttpsError(
            "invalid-argument",
            "El correo electrónico no es válido.",
        );
      }

      if (password.length < 6) {
        throw new HttpsError(
            "invalid-argument",
            "La contraseña debe tener al menos 6 caracteres.",
        );
      }

      if (licencia.length < 5) {
        throw new HttpsError(
            "invalid-argument",
            "Ingresa el número de licencia.",
        );
      }

      if (categoriaLicencia.length < 2) {
        throw new HttpsError(
            "invalid-argument",
            "Selecciona la categoría de la licencia.",
        );
      }

      if (vencimientoLicencia.length !== 10) {
        throw new HttpsError(
            "invalid-argument",
            "Selecciona el vencimiento de la licencia.",
        );
      }

      /*
       * Buscar el perfil del propietario.
       */
      const referenciaPropietario = db
          .collection("usuariosgestionpasajes")
          .doc(propietarioUid);

      const documentoPropietario =
        await referenciaPropietario.get();

      if (!documentoPropietario.exists) {
        throw new HttpsError(
            "permission-denied",
            "No se encontró el perfil del propietario.",
        );
      }

      const propietario =
        documentoPropietario.data() || {};

      const rol =
        obtenerTexto(propietario.rol).toLowerCase();

      const estado =
        obtenerTexto(propietario.estado).toLowerCase();

      const empresaId =
        obtenerTexto(propietario.empresaId);

      if (rol !== "propietario") {
        throw new HttpsError(
            "permission-denied",
            "Esta cuenta no pertenece a un propietario.",
        );
      }

      if (estado !== "activo") {
        throw new HttpsError(
            "permission-denied",
            "La cuenta del propietario no está activa.",
        );
      }

      if (!empresaId) {
        throw new HttpsError(
            "failed-precondition",
            "El propietario todavía no tiene una empresa.",
        );
      }

      /*
       * Verificar que la empresa exista.
       */
      const referenciaEmpresa = db
          .collection("empresaspasajes")
          .doc(empresaId);

      const documentoEmpresa =
        await referenciaEmpresa.get();

      if (!documentoEmpresa.exists) {
        throw new HttpsError(
            "failed-precondition",
            "La empresa asignada no existe.",
        );
      }

      const empresa =
        documentoEmpresa.data() || {};

      const estadoEmpresa =
        obtenerTexto(empresa.estado).toLowerCase();

      const empresaNombre =
        obtenerTexto(empresa.nombre) ||
        obtenerTexto(propietario.empresaNombre);

      if (
        estadoEmpresa &&
        estadoEmpresa !== "activo"
      ) {
        throw new HttpsError(
            "failed-precondition",
            "La empresa no se encuentra activa.",
        );
      }

      /*
       * Comprobar que no exista otro chofer
       * con el mismo DNI en esta empresa.
       */
      const consultaDni = await db
          .collection("choferespasajes")
          .where("dni", "==", dni)
          .limit(10)
          .get();

      const dniRepetido =
        consultaDni.docs.some((documento) => {
          const chofer = documento.data() || {};

          return obtenerTexto(chofer.empresaId) ===
            empresaId;
        });

      if (dniRepetido) {
        throw new HttpsError(
            "already-exists",
            "Ya existe un chofer registrado con ese DNI.",
        );
      }

      const nombreCompleto =
        `${nombres} ${apellidos}`.trim();

      let usuarioCreado = null;

      try {
        /*
         * Crear la cuenta del conductor
         * en Firebase Authentication.
         */
        usuarioCreado =
          await getAuth().createUser({
            email: correo,
            password: password,
            displayName: nombreCompleto,
            disabled: false,
          });

        /*
         * Crear el documento usado por ChoferYa.
         *
         * La contraseña NO se guarda en Firestore.
         */
        await db
            .collection("choferespasajes")
            .doc(usuarioCreado.uid)
            .set({
              uid: usuarioCreado.uid,

              empresaId: empresaId,
              empresaNombre: empresaNombre,

              nombres: nombres,
              apellidos: apellidos,
              nombreCompleto: nombreCompleto,

              dni: dni,
              celular: celular,
              correo: correo,

              licencia: licencia,
              categoriaLicencia: categoriaLicencia,
              vencimientoLicencia: vencimientoLicencia,

              vehiculoId: "",
              viajeActualId: "",

              rol: "chofer",
              estado: "activo",

              disponible: disponible,

              disponibilidad: disponible ?
                "disponible" :
                "no_disponible",

              debeCambiarPassword: true,

              creadoPorUid: propietarioUid,

              fechaRegistro:
                FieldValue.serverTimestamp(),

              fechaActualizacion:
                FieldValue.serverTimestamp(),
            });

        return {
          ok: true,
          uid: usuarioCreado.uid,
          nombres: nombreCompleto,
          correo: correo,
          empresaId: empresaId,
        };
      } catch (error) {
        /*
         * Si Authentication fue creado,
         * pero Firestore falló, eliminamos
         * la cuenta incompleta.
         */
        if (usuarioCreado) {
          try {
            await getAuth().deleteUser(
                usuarioCreado.uid,
            );
          } catch (errorEliminacion) {
            console.error(
                "No se pudo eliminar la cuenta incompleta:",
                errorEliminacion,
            );
          }
        }

        console.error(
            "Error al crear el chofer:",
            error,
        );

        if (error instanceof HttpsError) {
          throw error;
        }

        if (
          error.code ===
          "auth/email-already-exists"
        ) {
          throw new HttpsError(
              "already-exists",
              "El correo ya pertenece a otra cuenta.",
          );
        }

        if (
          error.code ===
          "auth/invalid-email"
        ) {
          throw new HttpsError(
              "invalid-argument",
              "El correo electrónico no es válido.",
          );
        }

        if (
          error.code ===
          "auth/invalid-password"
        ) {
          throw new HttpsError(
              "invalid-argument",
              "La contraseña no cumple los requisitos.",
          );
        }

        throw new HttpsError(
            "internal",
            "No se pudo crear la cuenta del chofer.",
        );
      }
    },
);


exports.crearVendedor = onCall(
    {
      region: "us-central1",
    },
    async (request) => {
      /*
       * Verificar que el propietario
       * esté autenticado.
       */
      if (!request.auth) {
        throw new HttpsError(
            "unauthenticated",
            "Debes iniciar sesión para crear un vendedor.",
        );
      }

      const propietarioUid = request.auth.uid;
      const datos = request.data || {};

      const nombres =
        obtenerTexto(datos.nombres);

      const apellidos =
        obtenerTexto(datos.apellidos);

      const dni =
        obtenerTexto(datos.dni);

      const celular =
        obtenerTexto(datos.celular);

      const correo =
        obtenerTexto(datos.correo).toLowerCase();

      const password =
        obtenerTexto(datos.password);

      /*
       * Validaciones.
       */
      if (nombres.length < 2) {
        throw new HttpsError(
            "invalid-argument",
            "Ingresa los nombres del vendedor.",
        );
      }

      if (apellidos.length < 2) {
        throw new HttpsError(
            "invalid-argument",
            "Ingresa los apellidos del vendedor.",
        );
      }

      if (
        dni.length !== 8 ||
        !soloNumeros(dni)
      ) {
        throw new HttpsError(
            "invalid-argument",
            "El DNI debe tener 8 dígitos.",
        );
      }

      if (
        celular.length !== 9 ||
        !soloNumeros(celular)
      ) {
        throw new HttpsError(
            "invalid-argument",
            "El celular debe tener 9 dígitos.",
        );
      }

      if (!esCorreoValido(correo)) {
        throw new HttpsError(
            "invalid-argument",
            "El correo electrónico no es válido.",
        );
      }

      if (password.length < 6) {
        throw new HttpsError(
            "invalid-argument",
            "La contraseña debe tener al menos 6 caracteres.",
        );
      }

      /*
       * Buscar al propietario.
       */
      const referenciaPropietario = db
          .collection("usuariosgestionpasajes")
          .doc(propietarioUid);

      const documentoPropietario =
        await referenciaPropietario.get();

      if (!documentoPropietario.exists) {
        throw new HttpsError(
            "permission-denied",
            "No se encontró el perfil del propietario.",
        );
      }

      const propietario =
        documentoPropietario.data() || {};

      const rolPropietario =
        obtenerTexto(propietario.rol).toLowerCase();

      const estadoPropietario =
        obtenerTexto(propietario.estado).toLowerCase();

      const empresaId =
        obtenerTexto(propietario.empresaId);

      if (rolPropietario !== "propietario") {
        throw new HttpsError(
            "permission-denied",
            "Esta cuenta no pertenece a un propietario.",
        );
      }

      if (estadoPropietario !== "activo") {
        throw new HttpsError(
            "permission-denied",
            "La cuenta del propietario no está activa.",
        );
      }

      if (!empresaId) {
        throw new HttpsError(
            "failed-precondition",
            "El propietario todavía no tiene una empresa.",
        );
      }

      /*
       * Verificar que la empresa exista.
       */
      const referenciaEmpresa = db
          .collection("empresaspasajes")
          .doc(empresaId);

      const documentoEmpresa =
        await referenciaEmpresa.get();

      if (!documentoEmpresa.exists) {
        throw new HttpsError(
            "failed-precondition",
            "La empresa asignada no existe.",
        );
      }

      const empresa =
        documentoEmpresa.data() || {};

      const estadoEmpresa =
        obtenerTexto(empresa.estado).toLowerCase();

      const empresaNombre =
        obtenerTexto(empresa.nombre) ||
        obtenerTexto(propietario.empresaNombre);

      if (
        estadoEmpresa &&
        estadoEmpresa !== "activo"
      ) {
        throw new HttpsError(
            "failed-precondition",
            "La empresa no se encuentra activa.",
        );
      }

      /*
       * Comprobar que el DNI no esté registrado
       * en otro usuario de la misma empresa.
       */
      const consultaDni = await db
          .collection("usuariosgestionpasajes")
          .where("dni", "==", dni)
          .get();

      const dniRepetido =
        consultaDni.docs.some((documento) => {
          const usuario = documento.data() || {};

          const mismaEmpresa =
            obtenerTexto(usuario.empresaId) ===
            empresaId;

          const estadoUsuario =
            obtenerTexto(usuario.estado).toLowerCase();

          return mismaEmpresa &&
            estadoUsuario !== "eliminado";
        });

      if (dniRepetido) {
        throw new HttpsError(
            "already-exists",
            "Ya existe un usuario registrado con ese DNI.",
        );
      }

      const nombreCompleto =
        `${nombres} ${apellidos}`.trim();

      let usuarioCreado = null;

      try {
        /*
         * Crear cuenta en Firebase Authentication.
         */
        usuarioCreado =
          await getAuth().createUser({
            email: correo,
            password: password,
            displayName: nombreCompleto,
            disabled: false,
          });

        /*
         * Crear perfil del vendedor.
         *
         * La contraseña nunca se guarda
         * en Firestore.
         */
        await db
            .collection("usuariosgestionpasajes")
            .doc(usuarioCreado.uid)
            .set({
              uid: usuarioCreado.uid,

              empresaId: empresaId,
              empresaNombre: empresaNombre,

              nombres: nombres,
              apellidos: apellidos,
              nombreCompleto: nombreCompleto,

              dni: dni,
              celular: celular,
              correo: correo,

              rol: "vendedor",
              estado: "activo",

              registroCompleto: true,
              debeCambiarPassword: true,

              creadoPorUid: propietarioUid,

              fechaRegistro:
                FieldValue.serverTimestamp(),

              fechaActualizacion:
                FieldValue.serverTimestamp(),
            });

        return {
          ok: true,
          uid: usuarioCreado.uid,
          nombres: nombreCompleto,
          correo: correo,
          empresaId: empresaId,
          empresaNombre: empresaNombre,
        };
      } catch (error) {
        /*
         * Si Authentication fue creado,
         * pero Firestore falló, eliminamos
         * la cuenta incompleta.
         */
        if (usuarioCreado) {
          try {
            await getAuth().deleteUser(
                usuarioCreado.uid,
            );
          } catch (errorEliminacion) {
            console.error(
                "No se pudo eliminar la cuenta incompleta:",
                errorEliminacion,
            );
          }
        }

        console.error(
            "Error al crear vendedor:",
            error,
        );

        if (error instanceof HttpsError) {
          throw error;
        }

        if (
          error.code ===
          "auth/email-already-exists"
        ) {
          throw new HttpsError(
              "already-exists",
              "El correo ya pertenece a otra cuenta.",
          );
        }

        if (
          error.code ===
          "auth/invalid-email"
        ) {
          throw new HttpsError(
              "invalid-argument",
              "El correo electrónico no es válido.",
          );
        }

        if (
          error.code ===
          "auth/invalid-password"
        ) {
          throw new HttpsError(
              "invalid-argument",
              "La contraseña no cumple los requisitos.",
          );
        }

        throw new HttpsError(
            "internal",
            "No se pudo crear la cuenta del vendedor.",
        );
      }
    },
);
